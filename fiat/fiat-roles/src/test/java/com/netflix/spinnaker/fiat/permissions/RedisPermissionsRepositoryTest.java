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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.BuildService;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import io.github.resilience4j.retry.RetryRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.jpountz.lz4.LZ4CompressorWithLength;
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
  private static final String UNRESTRICTED = UnrestrictedResourceConfig.UNRESTRICTED_USERNAME;
  private static final List<String> TYPE_SUFFIXES =
      List.of("applications", "accounts", "service_accounts", "roles", "build_services");

  private static GenericContainer<?> embeddedRedis;
  private static Jedis jedis;
  private static JedisPool jedisPool;
  private static JedisClientDelegate redisClientDelegate;
  private static LZ4CompressorWithLength lz4Compressor;
  private static LZ4DecompressorWithLength lz4Decompressor;

  private static final ObjectMapper objectMapper =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  // Matches the ObjectMapper bean Fiat is deployed with (see RetrofitConfig).
  private static final ObjectMapper productionObjectMapper =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(SerializationFeature.INDENT_OUTPUT, true)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private RedisPermissionRepositoryConfigProps configProps;
  private RedisPermissionsRepository repo;

  @BeforeAll
  static void setupRedis() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for RedisPermissionsRepositoryTest");
    embeddedRedis =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8")).withExposedPorts(6379);
    embeddedRedis.start();

    jedisPool = new JedisPool(embeddedRedis.getHost(), embeddedRedis.getMappedPort(6379));
    jedis = jedisPool.getResource();
    jedis.flushDB();

    redisClientDelegate = new JedisClientDelegate(jedisPool);

    LZ4Factory factory = LZ4Factory.fastestInstance();
    lz4Compressor = new LZ4CompressorWithLength(factory.fastCompressor());
    lz4Decompressor = new LZ4DecompressorWithLength(factory.fastDecompressor());
  }

  @BeforeEach
  void setup() {
    configProps = new RedisPermissionRepositoryConfigProps();
    configProps.setPrefix(PREFIX);
    repo = newRepo(objectMapper, redisClientDelegate);
  }

  @AfterEach
  void cleanup() {
    jedis.flushDB();
  }

  private RedisPermissionsRepository newRepo(
      ObjectMapper mapper, RedisClientDelegate clientDelegate) {
    return new RedisPermissionsRepository(
        Clock.systemUTC(),
        mapper,
        clientDelegate,
        List.of(
            new Application(), new Account(), new ServiceAccount(), new Role(), new BuildService()),
        configProps,
        RetryRegistry.ofDefaults());
  }

  private String getCompressed(String key) {
    byte[] k = SafeEncoder.encode(key);
    byte[] val = jedis.get(k);
    if (val == null) {
      return null;
    }
    return SafeEncoder.encode(lz4Decompressor.decompress(val));
  }

  private void setCompressed(String key, String value) {
    jedis.set(SafeEncoder.encode(key), lz4Compressor.compress(SafeEncoder.encode(value)));
  }

  private static String dataKey(String userId, String suffix) {
    return PREFIX + ":permissions-v2:" + userId + ":" + suffix;
  }

  private static String digestKey(String userId, String suffix) {
    return PREFIX + ":permissions-v2-digest:" + userId + ":" + suffix;
  }

  private void deleteDigests(String userId) {
    TYPE_SUFFIXES.forEach(suffix -> jedis.del(digestKey(userId, suffix)));
  }

  /** Runs the action and returns how many times Redis executed each of the given commands. */
  private Map<String, Long> countCommands(Runnable action, String... commands) {
    jedis.configResetStat();
    action.run();
    String stats = jedis.info("commandstats");
    Map<String, Long> counts = new HashMap<>();
    for (String command : commands) {
      counts.put(command, 0L);
    }
    for (String line : stats.split("\r?\n")) {
      for (String command : commands) {
        String statPrefix = "cmdstat_" + command + ":calls=";
        if (line.startsWith(statPrefix)) {
          String calls = line.substring(statPrefix.length()).split(",")[0];
          counts.put(command, Long.parseLong(calls));
        }
      }
    }
    return counts;
  }

  private static Permissions readWrite(String... roles) {
    Permissions.Builder builder = new Permissions.Builder();
    for (String role : roles) {
      builder.add(Authorization.READ, role).add(Authorization.WRITE, role);
    }
    return builder.build();
  }

  private static Application application(String name, String role) {
    Application application = new Application().setName(name).setPermissions(readWrite(role));
    application.setDetails("email", name + "@example.com");
    application.setDetails("cloudProviders", "kubernetes,aws");
    application.setDetails("nested", Map.of("list", List.of(1, 2, 3), "flag", true));
    return application;
  }

  private static UserPermission richUser(String id) {
    return new UserPermission()
        .setId(id)
        .setAccounts(
            Set.of(
                new Account()
                    .setName("prod")
                    .setCloudProvider("aws")
                    .setPermissions(readWrite("sre")),
                new Account().setName("staging").setPermissions(readWrite("dev", "sre")),
                new Account().setName("unicode-\u00e9\u00fc\"quoted\"")))
        .setApplications(
            Set.of(
                application("app-b", "dev"),
                application("app-a", "sre"),
                new Application().setName("bare")))
        .setServiceAccounts(
            Set.of(
                new ServiceAccount().setName("svc-1").setMemberOf(List.of("dev", "sre")),
                new ServiceAccount().setName("svc-2")))
        .setRoles(
            Set.of(
                new Role("dev").setSource(Role.Source.EXTERNAL),
                new Role("sre").setSource(Role.Source.LDAP),
                new Role("plain")))
        .setBuildServices(
            Set.of(new BuildService().setName("jenkins").setPermissions(readWrite("dev"))));
  }

  private static UserPermission withExtraAccount(UserPermission user) {
    Set<Account> accounts = new HashSet<>(user.getAccounts());
    accounts.add(new Account().setName("new-account"));
    return user.setAccounts(accounts);
  }

  private static Map<String, Resource> byName(Set<? extends Resource> resources) {
    return resources.stream().collect(Collectors.toMap(Resource::getName, Function.identity()));
  }

  @Test
  void storedValuesAreWhatTheObjectMapperWritesForTheSortedMap() throws Exception {
    for (ObjectMapper mapper : List.of(objectMapper, productionObjectMapper)) {
      RedisPermissionsRepository mapperRepo = newRepo(mapper, redisClientDelegate);
      UserPermission user = richUser("richUser");
      mapperRepo.put(user);

      Map<String, Set<? extends Resource>> bySuffix =
          Map.of(
              "accounts", user.getAccounts(),
              "applications", user.getApplications(),
              "service_accounts", user.getServiceAccounts(),
              "roles", user.getRoles(),
              "build_services", user.getBuildServices());
      Map<String, Class<? extends Resource>> modelBySuffix =
          Map.of(
              "accounts", Account.class,
              "applications", Application.class,
              "service_accounts", ServiceAccount.class,
              "roles", Role.class,
              "build_services", BuildService.class);

      for (String suffix : bySuffix.keySet()) {
        Map<String, Resource> resources = byName(bySuffix.get(suffix));
        String stored = getCompressed(dataKey("richuser", suffix));
        assertEquals(
            mapper.writeValueAsString(new TreeMap<>(resources)),
            stored,
            suffix + " should be byte-identical to serializing the sorted map");

        // What the previous implementation wrote (an unsorted HashMap) must read back the same.
        byte[] previous = mapper.writeValueAsBytes(new HashMap<>(resources));
        assertEquals(mapper.readTree(previous), mapper.readTree(stored));
        Map<String, ? extends Resource> readPrevious =
            mapper.readerForMapOf(modelBySuffix.get(suffix)).readValue(previous);
        Map<String, ? extends Resource> readStored =
            mapper.readerForMapOf(modelBySuffix.get(suffix)).readValue(stored);
        assertEquals(
            mapper.readTree(mapper.writeValueAsBytes(new TreeMap<>(readPrevious))),
            mapper.readTree(mapper.writeValueAsBytes(new TreeMap<>(readStored))));
      }

      UserPermission read = mapperRepo.get("richuser").orElseThrow();
      assertEquals(user.getAccounts(), read.getAccounts());
      assertEquals(user.getApplications(), read.getApplications());
      assertEquals(user.getServiceAccounts(), read.getServiceAccounts());
      assertEquals(user.getRoles(), read.getRoles());
      assertEquals(user.getBuildServices(), read.getBuildServices());
      Application appA =
          read.getApplications().stream()
              .filter(a -> a.getName().equals("app-a"))
              .findFirst()
              .orElseThrow();
      assertEquals(application("app-a", "sre").getDetails(), appA.getDetails());
      assertEquals(readWrite("sre"), appA.getPermissions());
      Role sre =
          read.getRoles().stream().filter(r -> r.getName().equals("sre")).findFirst().orElseThrow();
      assertEquals(Role.Source.LDAP, sre.getSource());

      jedis.flushDB();
    }
  }

  @Test
  void emptyResourceTypesAreNotStored() {
    repo.put(new UserPermission().setId("emptyUser"));

    for (String suffix : TYPE_SUFFIXES) {
      assertFalse(jedis.exists(dataKey("emptyuser", suffix)));
      assertFalse(jedis.exists(digestKey("emptyuser", suffix)));
    }
    UserPermission read = repo.get("emptyuser").orElseThrow();
    assertTrue(read.getAllResources().isEmpty());

    repo.put(richUser("emptyUser"));
    repo.put(new UserPermission().setId("emptyUser"));

    for (String suffix : TYPE_SUFFIXES) {
      assertFalse(jedis.exists(dataKey("emptyuser", suffix)), suffix + " should be deleted");
      assertFalse(
          jedis.exists(digestKey("emptyuser", suffix)), suffix + " digest should be deleted");
    }
  }

  @Test
  void unchangedPutDoesNotRewriteKeysOrRoleSets() {
    repo.put(richUser("steady"));
    for (String suffix : TYPE_SUFFIXES) {
      assertEquals(-1, jedis.pttl(digestKey("steady", suffix)), "digests should not expire");
    }

    Map<String, Long> counts =
        countCommands(() -> repo.put(richUser("steady")), "set", "rename", "psetex", "sadd", "get");

    assertEquals(0, counts.get("set"));
    assertEquals(0, counts.get("rename"));
    assertEquals(0, counts.get("psetex"));
    assertEquals(1, counts.get("sadd"), "only the all-users set should be touched");
    assertEquals(TYPE_SUFFIXES.size(), counts.get("get"), "only digests should be read");
    assertEquals(Set.of("steady"), jedis.smembers(PREFIX + ":roles:dev"));
  }

  @Test
  void changedContentIsRewritten() throws Exception {
    repo.put(richUser("changing"));

    UserPermission changed = withExtraAccount(richUser("changing"));
    Map<String, Long> counts = countCommands(() -> repo.put(changed), "rename", "set", "sadd");

    assertEquals(1, counts.get("rename"), "only the accounts key changed");
    assertEquals(2, counts.get("set"), "temp key and digest");
    assertEquals(1, counts.get("sadd"), "roles did not change");
    assertEquals(
        objectMapper.writeValueAsString(new TreeMap<>(byName(changed.getAccounts()))),
        getCompressed(dataKey("changing", "accounts")));
  }

  @Test
  void roleChangesUpdateRoleSets() {
    repo.put(
        new UserPermission().setId("roleUser").setRoles(Set.of(new Role("r1"), new Role("r2"))));
    assertTrue(jedis.sismember(PREFIX + ":roles:r2", "roleuser"));

    repo.put(
        new UserPermission().setId("roleUser").setRoles(Set.of(new Role("r1"), new Role("r3"))));

    assertTrue(jedis.sismember(PREFIX + ":roles:r1", "roleuser"));
    assertFalse(jedis.sismember(PREFIX + ":roles:r2", "roleuser"));
    assertTrue(jedis.sismember(PREFIX + ":roles:r3", "roleuser"));

    repo.put(new UserPermission().setId("roleUser"));

    assertFalse(jedis.sismember(PREFIX + ":roles:r1", "roleuser"));
    assertFalse(jedis.sismember(PREFIX + ":roles:r3", "roleuser"));
    assertEquals(Map.of("roleuser", Set.of()), repo.getAllById());
  }

  @Test
  void removeClearsDigestsSoTheNextPutRewrites() {
    repo.put(richUser("removed"));
    repo.remove("removed");

    assertEquals(Set.of(), jedis.keys("*"));

    Map<String, Long> counts = countCommands(() -> repo.put(richUser("removed")), "rename");
    assertEquals(TYPE_SUFFIXES.size(), counts.get("rename"));
    assertTrue(jedis.sismember(PREFIX + ":roles:dev", "removed"));
  }

  @Test
  void missingDigestsFromPreviousVersionsTriggerRewrites() {
    // Data as written by a Fiat version without digests: unsorted and with a role since revoked.
    jedis.sadd(PREFIX + ":users", "legacy");
    jedis.sadd(PREFIX + ":roles:revoked", "legacy");
    setCompressed(dataKey("legacy", "roles"), "{\"revoked\":{\"name\":\"revoked\"}}");
    setCompressed(
        dataKey("legacy", "accounts"),
        "{\"staging\":{\"name\":\"staging\",\"permissions\":{}},\"prod\":{\"name\":\"prod\"}}");
    setCompressed(dataKey("legacy", "build_services"), "{\"old\":{\"name\":\"old\"}}");

    UserPermission user = richUser("legacy").setBuildServices(Set.of());
    Map<String, Long> counts = countCommands(() -> repo.put(user), "rename", "del");

    assertEquals(TYPE_SUFFIXES.size() - 1, counts.get("rename"));
    assertEquals(2, counts.get("del"), "build services data and digest");
    assertFalse(jedis.exists(dataKey("legacy", "build_services")));
    assertFalse(jedis.sismember(PREFIX + ":roles:revoked", "legacy"));
    assertTrue(jedis.sismember(PREFIX + ":roles:dev", "legacy"));
    assertEquals(user.getAccounts(), repo.get("legacy").orElseThrow().getAccounts());

    deleteDigests("legacy");
    counts = countCommands(() -> repo.put(user), "rename");
    assertEquals(TYPE_SUFFIXES.size() - 1, counts.get("rename"));
  }

  @Test
  void writesByDigestUnawareVersionsAreDetected() throws Exception {
    UserPermission user = richUser("mixed");
    repo.put(user);
    String accounts = getCompressed(dataKey("mixed", "accounts"));

    // An older Fiat instance rewrote the accounts key and deleted the roles key, leaving the
    // digests behind.
    setCompressed(dataKey("mixed", "accounts"), "{\"other\":{\"name\":\"other\"}}");
    jedis.del(dataKey("mixed", "roles"));
    jedis.srem(PREFIX + ":roles:dev", "mixed");

    Map<String, Long> counts = countCommands(() -> repo.put(user), "rename");

    assertEquals(2, counts.get("rename"));
    assertEquals(accounts, getCompressed(dataKey("mixed", "accounts")));
    assertEquals(user.getRoles(), repo.get("mixed").orElseThrow().getRoles());
    assertTrue(jedis.sismember(PREFIX + ":roles:dev", "mixed"));
  }

  @Test
  void failedWritesDropDigests() {
    UserPermission original = richUser("failing");
    repo.put(original);

    AtomicBoolean failNextWrite = new AtomicBoolean(true);
    int[] pipelines = {0};
    RedisClientDelegate failingDelegate =
        (RedisClientDelegate)
            Proxy.newProxyInstance(
                RedisClientDelegate.class.getClassLoader(),
                new Class<?>[] {RedisClientDelegate.class},
                (proxy, method, args) -> {
                  // The second pipeline of a put is the write; the first reads digests.
                  if (method.getName().equals("withMultiKeyPipeline")
                      && ++pipelines[0] == 2
                      && failNextWrite.getAndSet(false)) {
                    throw new IllegalStateException("simulated write failure");
                  }
                  try {
                    return method.invoke(redisClientDelegate, args);
                  } catch (InvocationTargetException e) {
                    throw e.getCause();
                  }
                });

    UserPermission changed = withExtraAccount(richUser("failing"));
    newRepo(objectMapper, failingDelegate).put(changed);

    for (String suffix : TYPE_SUFFIXES) {
      assertFalse(jedis.exists(digestKey("failing", suffix)), suffix + " digest should be dropped");
    }
    assertEquals(original.getAccounts(), repo.get("failing").orElseThrow().getAccounts());

    Map<String, Long> counts = countCommands(() -> repo.put(changed), "rename");
    assertEquals(TYPE_SUFFIXES.size(), counts.get("rename"));
    assertEquals(changed.getAccounts(), repo.get("failing").orElseThrow().getAccounts());
  }

  @Test
  void abortedTransactionDropsDigests() {
    UserPermission original = richUser("oom");
    repo.put(original);
    UserPermission changed = withExtraAccount(richUser("oom"));

    // With no memory left, Redis rejects the queued writes and aborts the whole EXEC.
    jedis.configSet("maxmemory-policy", "noeviction");
    jedis.configSet("maxmemory", "1");
    try {
      repo.put(changed);
    } finally {
      jedis.configSet("maxmemory", "0");
    }

    for (String suffix : TYPE_SUFFIXES) {
      assertFalse(jedis.exists(digestKey("oom", suffix)), suffix + " digest should be dropped");
    }
    assertEquals(original.getAccounts(), repo.get("oom").orElseThrow().getAccounts());

    repo.put(changed);
    assertEquals(changed.getAccounts(), repo.get("oom").orElseThrow().getAccounts());
  }

  @Test
  void disablingSkipUnchangedWritesForcesWritesAndKeepsDigestsAccurate() {
    configProps.getRepository().setSkipUnchangedWrites(false);
    repo.put(richUser("forced"));

    Map<String, Long> counts =
        countCommands(() -> repo.put(richUser("forced")), "rename", "get", "strlen");

    assertEquals(TYPE_SUFFIXES.size(), counts.get("rename"));
    assertEquals(0, counts.get("strlen"), "digests should not be consulted");
    assertEquals(TYPE_SUFFIXES.size(), jedis.keys(PREFIX + ":permissions-v2-digest:*").size());

    configProps.getRepository().setSkipUnchangedWrites(true);
    counts = countCommands(() -> repo.put(richUser("forced")), "rename");
    assertEquals(0, counts.get("rename"), "digests written while disabled are valid");
  }

  @Test
  void zeroDigestTtlMeansNoExpiryAndSkippingStillApplies() {
    configProps.getRepository().setWriteDigestTtl(Duration.ZERO);
    repo.put(richUser("zeroTtl"));

    assertEquals(-1, jedis.pttl(digestKey("zerottl", "accounts")));
    Map<String, Long> counts = countCommands(() -> repo.put(richUser("zeroTtl")), "rename");
    assertEquals(0, counts.get("rename"));
  }

  @Test
  void positiveDigestTtlExpiresDigests() {
    configProps.getRepository().setWriteDigestTtl(Duration.ofMinutes(30));
    repo.put(richUser("ttlUser"));

    for (String suffix : TYPE_SUFFIXES) {
      long pttl = jedis.pttl(digestKey("ttluser", suffix));
      assertTrue(pttl > 0 && pttl <= Duration.ofMinutes(30).toMillis(), suffix + " pttl " + pttl);
    }
    Map<String, Long> counts = countCommands(() -> repo.put(richUser("ttlUser")), "rename");
    assertEquals(0, counts.get("rename"));
  }

  @Test
  void changingDigestGenerationInvalidatesStoredDigests() {
    repo.put(richUser("generational"));

    configProps.getRepository().setWriteDigestGeneration("2");
    Map<String, Long> counts =
        countCommands(() -> repo.put(richUser("generational")), "rename", "sadd");
    assertEquals(TYPE_SUFFIXES.size(), counts.get("rename"));
    assertTrue(counts.get("sadd") > 1, "role sets should be rewritten too");

    counts = countCommands(() -> repo.put(richUser("generational")), "rename");
    assertEquals(0, counts.get("rename"));
  }

  @Test
  void unrestrictedUserLastModifiedIsUpdatedOnPut() {
    String lastModifiedKey = PREFIX + ":last_modified:" + UNRESTRICTED;
    repo.put(
        new UserPermission()
            .setId(UNRESTRICTED)
            .setAccounts(Set.of(new Account().setName("shared"))));
    repo.put(new UserPermission().setId("someone"));
    assertNotNull(jedis.get(lastModifiedKey));

    jedis.set(lastModifiedKey, "0");
    Set<Account> changed = Set.of(new Account().setName("shared"), new Account().setName("more"));
    repo.put(new UserPermission().setId(UNRESTRICTED).setAccounts(changed));

    assertNotEquals("0", jedis.get(lastModifiedKey));
    assertEquals(changed, repo.get(UNRESTRICTED).orElseThrow().getAccounts());
    assertEquals(changed, repo.get("someone").orElseThrow().getAccounts());

    jedis.set(lastModifiedKey, "0");
    repo.put(new UserPermission().setId(UNRESTRICTED).setAccounts(changed));
    assertNotEquals("0", jedis.get(lastModifiedKey), "bumped even when unchanged");
  }

  @Test
  void putAllByIdKeepsEqualButDifferentResourcesApart() throws Exception {
    // Applications are equal by name, so a cache keyed by equals() would store one user's copy for
    // both.
    Application forUser1 = application("shared-app", "role1");
    Application forUser2 = application("shared-app", "role2");
    Application common = application("common-app", "everyone");

    repo.putAllById(
        Map.of(
            "user1",
            new UserPermission().setId("user1").setApplications(Set.of(forUser1, common)),
            "user2",
            new UserPermission().setId("user2").setApplications(Set.of(forUser2, common))));

    assertEquals(
        objectMapper.writeValueAsString(
            new TreeMap<>(Map.of("shared-app", forUser1, "common-app", common))),
        getCompressed(dataKey("user1", "applications")));
    assertEquals(
        objectMapper.writeValueAsString(
            new TreeMap<>(Map.of("shared-app", forUser2, "common-app", common))),
        getCompressed(dataKey("user2", "applications")));
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
