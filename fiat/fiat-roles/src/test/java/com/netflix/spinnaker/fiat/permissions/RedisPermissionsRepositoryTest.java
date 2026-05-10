/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use it except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.BuildService;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import net.jpountz.lz4.LZ4DecompressorWithLength;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.SafeEncoder;

class RedisPermissionsRepositoryTest {

  private static final String PREFIX = "unittests";

  private static GenericContainer<?> embeddedRedis;
  private static Jedis jedis;
  private static JedisPool jedisPool;
  private static JedisClientDelegate redisClientDelegate;
  private static LZ4DecompressorWithLength lz4Decompressor;

  private static final ObjectMapper objectMapper =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private RedisPermissionRepositoryConfigProps configProps;
  private RedisPermissionsRepository repo;

  @BeforeAll
  static void setupRedis() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for RedisPermissionsRepositoryTest");
    embeddedRedis =
        new GenericContainer<>(DockerImageName.parse("library/redis:5-alpine"))
            .withExposedPorts(6379);
    embeddedRedis.start();

    jedisPool = new JedisPool(embeddedRedis.getHost(), embeddedRedis.getMappedPort(6379));
    jedis = jedisPool.getResource();
    jedis.flushDB();

    redisClientDelegate = new JedisClientDelegate(jedisPool);

    LZ4Factory factory = LZ4Factory.fastestInstance();
    lz4Decompressor = new LZ4DecompressorWithLength(factory.fastDecompressor());
  }

  @BeforeEach
  void setup() {
    configProps = new RedisPermissionRepositoryConfigProps();
    configProps.setPrefix(PREFIX);

    repo =
        new RedisPermissionsRepository(
            Clock.systemUTC(),
            objectMapper,
            redisClientDelegate,
            java.util.List.of(
                new Application(),
                new Account(),
                new ServiceAccount(),
                new Role(),
                new BuildService()),
            configProps,
            RetryRegistry.ofDefaults());
  }

  @AfterEach
  void cleanup() {
    jedis.flushDB();
  }

  private String getCompressed(String key) {
    byte[] k = SafeEncoder.encode(key);
    byte[] val = jedis.get(k);
    if (val == null) {
      return null;
    }
    return SafeEncoder.encode(lz4Decompressor.decompress(val));
  }

  @Test
  void putAllByIdShouldPersistManyUsersInParallel() {
    Map<String, UserPermission> permissions = new HashMap<>();
    for (int i = 1; i <= 20; i++) {
      UserPermission user =
          new UserPermission()
              .setId("parallelUser" + i)
              .setAccounts(java.util.Set.of(new Account().setName("account" + i)));
      permissions.put("paralleluser" + i, user);
    }

    repo.putAllById(permissions);

    assertEquals(20, jedis.scard(PREFIX + ":users"));
    for (int i = 1; i <= 20; i++) {
      assertTrue(
          jedis.sismember(PREFIX + ":users", "paralleluser" + i),
          "User paralleluser" + i + " should be in users set");
      assertNotNull(
          getCompressed(PREFIX + ":permissions-v2:paralleluser" + i + ":accounts"),
          "Accounts for paralleluser" + i + " should be persisted");
    }
  }

  @Test
  void getAllByIdShouldReadManyUsersInParallel() {
    Map<String, UserPermission> permissions = new HashMap<>();
    for (int i = 1; i <= 25; i++) {
      Role role = new Role("role" + (i % 5));
      UserPermission user =
          new UserPermission()
              .setId("getAllUser" + i)
              .setRoles(java.util.Set.of(role))
              .setAccounts(java.util.Set.of(new Account().setName("account" + i)));
      permissions.put("getalluser" + i, user);
    }

    repo.putAllById(permissions);

    Map<String, java.util.Set<Role>> result = repo.getAllById();

    assertEquals(25, result.size());
    for (int i = 1; i <= 25; i++) {
      final int userIndex = i;
      String userId = "getalluser" + i;
      assertTrue(result.containsKey(userId), "User " + userId + " should be in result");
      java.util.Set<Role> roles = result.get(userId);
      assertNotNull(roles, "Roles for " + userId + " should not be null");
      assertEquals(1, roles.size(), "User " + userId + " should have 1 role");
      assertTrue(
          roles.stream().anyMatch(r -> r.getName().equals("role" + (userIndex % 5))),
          "User " + userId + " should have role" + (userIndex % 5));
    }
  }
}
