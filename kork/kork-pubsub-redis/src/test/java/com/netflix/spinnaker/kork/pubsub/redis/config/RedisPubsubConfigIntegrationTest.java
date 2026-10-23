/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.kork.pubsub.redis.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.pubsub.PubsubBroadcastPublishers;
import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.config.PubsubConfig;
import com.netflix.spinnaker.kork.pubsub.model.BroadcastMessage;
import com.netflix.spinnaker.kork.pubsub.redis.broadcast.api.RedisBroadcastMessageHandlerFactory;
import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageHandlerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots {@link RedisPubsubConfig} the way a host application would, against a real Valkey instance.
 * This is the only test that exercises the configuration's {@code @Import}s of Spring Boot's Redis
 * and Integration auto-configurations, so it guards against those classes moving (as they did in
 * Spring Boot 4) and against Spring Integration endpoints failing to initialize.
 */
class RedisPubsubConfigIntegrationTest {

  private static GenericContainer<?> valkey;

  private static final List<MapRecord<String, String, String>> streamRecords =
      new CopyOnWriteArrayList<>();
  private static final List<BroadcastMessage> broadcastMessages = new CopyOnWriteArrayList<>();

  @BeforeAll
  static void startValkey() {
    valkey =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8")).withExposedPorts(6379);
    valkey.start();
  }

  @AfterAll
  static void stopValkey() {
    if (valkey != null) {
      valkey.stop();
    }
  }

  @Test
  void streamAndBroadcastMessagesRoundTripThroughTheWiredContext() {
    String suffix = UUID.randomUUID().toString();

    new ApplicationContextRunner()
        .withUserConfiguration(HostConfig.class, PubsubConfig.class, RedisPubsubConfig.class)
        .withPropertyValues(
            "pubsub.enabled=true",
            "pubsub.redis.enabled=true",
            "spring.data.redis.host=" + valkey.getHost(),
            "spring.data.redis.port=" + valkey.getMappedPort(6379),
            "pubsub.redis.subscriptions[0].name=stream-" + suffix,
            "pubsub.redis.broadcasts[0].name=broadcast-" + suffix,
            "pubsub.redis.broadcasts[0].channel=channel-" + suffix)
        .run(
            context -> {
              assertThat(context).hasNotFailed();

              // The broadcast publisher also implements the base PubsubPublisher, so select the
              // Streams publisher by name rather than publishing to every "redis" publisher.
              context.getBean(PubsubPublishers.class).withType(RedisPubsubConfig.SYSTEM).stream()
                  .filter(publisher -> publisher.getName().equals("stream-" + suffix))
                  .forEach(publisher -> publisher.publish("stream-hello"));
              awaitUntil(
                  () ->
                      streamRecords.stream()
                          .anyMatch(record -> record.getValue().containsValue("stream-hello")));

              var broadcastPublishers = context.getBean(PubsubBroadcastPublishers.class).getAll();
              assertThat(broadcastPublishers).hasSize(1);
              broadcastPublishers.forEach(publisher -> publisher.publish("broadcast-hello"));
              awaitUntil(
                  () ->
                      broadcastMessages.stream()
                          .anyMatch(message -> message.getBody().equals("broadcast-hello")));
            });
  }

  private static void awaitUntil(BooleanSupplier received) throws InterruptedException {
    long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
    while (!received.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Timed out waiting for the published message to be received");
      }
      Thread.sleep(50);
    }
  }

  @Configuration
  static class HostConfig {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    RedisStreamMessageHandlerFactory redisStreamMessageHandlerFactory() {
      return subscription -> streamRecords::add;
    }

    @Bean
    RedisBroadcastMessageHandlerFactory redisBroadcastMessageHandlerFactory() {
      return subscription -> broadcastMessages::add;
    }
  }
}
