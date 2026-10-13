/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.sql.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.jackson.mixins.CredentialsDefinitionMixin;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionMapper;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRefreshListener;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSecretManager;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.clouddriver.sql.SqlRetries;
import com.netflix.spinnaker.config.ConnectionPools;
import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.credentials.definition.CredentialsLoader;
import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import com.netflix.spinnaker.kork.pubsub.redis.RedisMessageChannel;
import com.netflix.spinnaker.kork.pubsub.redis.RedisPubsub;
import com.netflix.spinnaker.kork.secrets.SecretSession;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class SqlAccountDefinitionNotificationsTest {
  private static final String POOL = ConnectionPools.ACCOUNTS.getValue();
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
  private static SqlTestUtil.TestDatabase database;
  private static LettuceConnectionFactory redisConnection;
  private RedisMessageChannel channel;
  private SqlAccountDefinitionNotifications notifications;
  private SqlAccountDefinitionRepository repository;
  private AccountDefinitionMapper mapper;
  private final List<AccountDefinitionRefreshListener> replicas = new ArrayList<>();
  private final List<MapBackedCredentialsRepository<TestCredentials>> credentials =
      new ArrayList<>();
  private final List<PubsubMessage> messages = new CopyOnWriteArrayList<>();
  private MessageSubscription observer;

  @BeforeAll
  static void startServices() {
    database = SqlTestUtil.initTcMysqlDatabase();
    REDIS.start();
    redisConnection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    redisConnection.afterPropertiesSet();
    redisConnection.start();
  }

  @AfterAll
  static void stopServices() throws Exception {
    if (redisConnection != null) {
      redisConnection.destroy();
    }
    REDIS.stop();
    if (database != null) {
      database.close();
    }
  }

  @BeforeEach
  void setUp() {
    SqlTestUtil.cleanupDb(database.context);
    StringRedisTemplate redis = new StringRedisTemplate(redisConnection);
    try (var connection = redisConnection.getConnection()) {
      connection.serverCommands().flushAll();
    }
    channel =
        new RedisPubsub(redis, new PubsubPublishers(), new PubsubSubscribers())
            .broadcast("accounts");
    notifications =
        new SqlAccountDefinitionNotifications(database.context, channel, POOL, new SqlRetries());
    ObjectMapper jackson =
        new ObjectMapper().addMixIn(CredentialsDefinition.class, CredentialsDefinitionMixin.class);
    jackson.registerSubtypes(TestDefinition.class);
    mapper =
        new AccountDefinitionMapper(
            jackson, mock(AccountDefinitionSecretManager.class), mock(SecretSession.class));
    repository = repository(database.context, notifications);
    AtomicReference<MessageSubscription> reference = new AtomicReference<>();
    observer =
        channel.subscribe(
            "observer",
            1,
            message -> {
              reference.get().ack(message);
              messages.add(message);
            },
            error -> {
              throw new AssertionError(error);
            });
    reference.set(observer);
    observer.start();
    for (int i = 0; i < 2; i++) {
      startReplica();
    }
  }

  @AfterEach
  void closeSubscriptions() {
    replicas.forEach(AccountDefinitionRefreshListener::destroy);
    if (observer != null) {
      observer.close();
    }
  }

  @Test
  void everyCommittedOperationRefreshesCredentialsOnEveryReplicaWithoutSendingSecrets() {
    repository.create(new TestDefinition("account", "secret-create"));
    publishAndExpect("account", "secret-create", 1);
    repository.save(new TestDefinition("account", "secret-save"));
    publishAndExpect("account", "secret-save", 2);
    repository.update(new TestDefinition("account", "secret-update"));
    publishAndExpect("account", "secret-update", 3);
    repository.save(new TestDefinition("new-account", "secret-new"));
    publishAndExpect("new-account", "secret-new", 4);
    repository.delete("account");
    notifications.publishCommitted();
    await()
        .untilAsserted(
            () -> {
              assertThat(messages).hasSize(5);
              credentials.forEach(replica -> assertThat(replica.has("account")).isFalse());
            });
    assertThat(messages)
        .allSatisfy(
            message -> {
              assertThat(message.payload()).isEqualTo("accounts-changed");
              assertThat(message.attributes()).isEmpty();
            });
    assertThat(outboxSize()).isZero();
  }

  @Test
  void failedWritesAndRolledBackHistoryNeverPublish() {
    repository.create(new TestDefinition("existing", "secret"));
    publishAndExpect("existing", "secret", 1);
    assertThatThrownBy(() -> repository.create(new TestDefinition("existing", "duplicate")))
        .isInstanceOf(SqlAccountDefinitionException.class);
    assertThatThrownBy(() -> repository.update(new TestDefinition("missing", "secret")))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> repository.delete("missing")).isInstanceOf(RuntimeException.class);
    database.context.execute(
        "ALTER TABLE accounts_history ADD CONSTRAINT reject_broken CHECK (id <> 'broken' AND (id <> 'existing' OR version = 1))");
    try {
      assertThatThrownBy(() -> repository.save(new TestDefinition("broken", "secret")))
          .isInstanceOf(SqlAccountDefinitionException.class);
      assertThat(repository.getByName("broken")).isNull();
      assertThatThrownBy(() -> repository.save(new TestDefinition("existing", "failed-save")))
          .isInstanceOf(SqlAccountDefinitionException.class);
      assertThatThrownBy(() -> repository.update(new TestDefinition("existing", "failed-update")))
          .isInstanceOf(SqlAccountDefinitionException.class);
      assertThatThrownBy(() -> repository.delete("existing"))
          .isInstanceOf(SqlAccountDefinitionException.class);
      assertThat(((TestDefinition) repository.getByName("existing")).password())
          .isEqualTo("secret");
    } finally {
      database.context.execute("ALTER TABLE accounts_history DROP CHECK reject_broken");
    }
    assertThat(outboxSize()).isZero();
    notifications.publishCommitted();
    assertThat(messages).hasSize(1);
  }

  @Test
  void outerTransactionsDoNotNotifyBeforeCommitAndRollbackLeavesNoNotification() {
    database.context.transaction(
        configuration -> {
          repository(DSL.using(configuration), notifications)
              .create(new TestDefinition("committed", "secret"));
          notifications.publishCommitted();
          assertThat(messages).isEmpty();
          assertThat(outboxSize()).isZero();
        });
    publishAndExpect("committed", "secret", 1);
    assertThatThrownBy(
            () ->
                database.context.transaction(
                    configuration -> {
                      repository(DSL.using(configuration), notifications)
                          .create(new TestDefinition("rolled-back", "secret"));
                      notifications.publishCommitted();
                      throw new IllegalStateException("roll back outer transaction");
                    }))
        .isInstanceOf(RuntimeException.class);
    assertThat(repository.getByName("rolled-back")).isNull();
    assertThat(outboxSize()).isZero();
    notifications.publishCommitted();
    assertThat(messages).hasSize(1);
  }

  @Test
  void redisFailureKeepsTheCommittedOutboxRowForRetry() {
    MessageChannel failingChannel = mock(MessageChannel.class);
    doThrow(new IllegalStateException("Redis unavailable"))
        .doNothing()
        .when(failingChannel)
        .publish("accounts-changed");
    SqlAccountDefinitionNotifications retrying =
        new SqlAccountDefinitionNotifications(
            database.context, failingChannel, POOL, new SqlRetries());
    repository(database.context, retrying).create(new TestDefinition("account", "secret"));
    retrying.publishCommitted();
    assertThat(repository.getByName("account")).isNotNull();
    assertThat(outboxSize()).isEqualTo(1);
    retrying.publishCommitted();
    verify(failingChannel, times(2)).publish("accounts-changed");
    assertThat(outboxSize()).isZero();
  }

  @Test
  void replicaStartingAfterAChangeLoadsCurrentCredentialsWithoutAnOldBroadcast() {
    repository.create(new TestDefinition("before-start", "secret"));
    notifications.publishCommitted();
    startReplica();
    assertThat(credentials.getLast().getOne("before-start").password()).isEqualTo("secret");
  }

  @Test
  void failedCredentialReloadRemainsUnacknowledgedUntilMaintenanceRetriesIt() {
    AtomicBoolean fail = new AtomicBoolean();
    AtomicInteger loads = new AtomicInteger();
    CredentialsLoader<Credentials> loader =
        new CredentialsLoader<>() {
          @Override
          public MapBackedCredentialsRepository<Credentials> getCredentialsRepository() {
            return new MapBackedCredentialsRepository<>("test", null);
          }

          @Override
          public void load() {
            if (fail.getAndSet(false)) {
              throw new IllegalStateException("temporary source failure");
            }
            loads.incrementAndGet();
          }
        };
    ObjectProvider<CredentialsLoader<? extends Credentials>> provider =
        new ObjectProvider<>() {
          @Override
          public Stream<CredentialsLoader<? extends Credentials>> stream() {
            return Stream.of(loader);
          }
        };
    AccountDefinitionRefreshListener listener =
        new AccountDefinitionRefreshListener(channel, provider, 0, 100_000);
    listener.onApplicationEvent(null);
    replicas.add(listener);
    fail.set(true);
    repository.create(new TestDefinition("account", "secret"));
    notifications.publishCommitted();
    StringRedisTemplate redis = new StringRedisTemplate(redisConnection);
    await()
        .untilAsserted(
            () -> {
              assertThat(fail).isFalse();
              assertThat(
                      redis.opsForStream().groups(channel.getStreamKey()).stream()
                          .mapToLong(group -> group.pendingCount())
                          .sum())
                  .isEqualTo(1);
            });
    listener.maintain();
    assertThat(loads).hasValue(2);
    assertThat(
            redis.opsForStream().groups(channel.getStreamKey()).stream()
                .mapToLong(group -> group.pendingCount())
                .sum())
        .isZero();
  }

  private void startReplica() {
    MapBackedCredentialsRepository<TestCredentials> local =
        new MapBackedCredentialsRepository<>("test", null);
    BasicCredentialsLoader<TestDefinition, TestCredentials> loader =
        new BasicCredentialsLoader<>(
            new AccountDefinitionSource<>(repository, TestDefinition.class, List.of()),
            definition -> new TestCredentials(definition.name(), definition.password()),
            local);
    // The existing @PostConstruct path loads before readiness; readiness catches intervening
    // writes.
    loader.load();
    ObjectProvider<CredentialsLoader<? extends Credentials>> provider =
        new ObjectProvider<>() {
          @Override
          public Stream<CredentialsLoader<? extends Credentials>> stream() {
            return Stream.of(loader);
          }
        };
    RedisMessageChannel replicaChannel =
        new RedisPubsub(
                new StringRedisTemplate(redisConnection),
                new PubsubPublishers(),
                new PubsubSubscribers())
            .broadcast("accounts");
    AccountDefinitionRefreshListener listener =
        new AccountDefinitionRefreshListener(replicaChannel, provider, 0, 100_000);
    listener.onApplicationEvent(null);
    replicas.add(listener);
    credentials.add(local);
  }

  private SqlAccountDefinitionRepository repository(
      DSLContext context, SqlAccountDefinitionNotifications notifier) {
    return new SqlAccountDefinitionRepository(
        context, mapper, Clock.systemUTC(), POOL, new SqlRetries(), notifier);
  }

  private void publishAndExpect(String name, String password, int count) {
    assertThat(messages).hasSize(count - 1);
    notifications.publishCommitted();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(messages).hasSize(count);
              credentials.forEach(
                  replica -> {
                    assertThat(replica.getOne(name)).isNotNull();
                    assertThat(replica.getOne(name).password()).isEqualTo(password);
                  });
            });
  }

  private int outboxSize() {
    return database.context.fetchCount(DSL.table("account_notifications"));
  }

  @JsonTypeName("test")
  public record TestDefinition(String name, String password) implements CredentialsDefinition {
    @Override
    public String getName() {
      return name;
    }
  }

  public record TestCredentials(String name, String password) implements Credentials {
    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getType() {
      return "test";
    }
  }
}
