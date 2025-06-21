/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.permissions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.*;
import redis.clients.jedis.*;
import redis.clients.jedis.commands.JedisBinaryCommands;
import redis.clients.jedis.util.SafeEncoder;

/**
 * This Redis-backed permission repository is structured in a way to optimized reading types of
 * resource permissions. In general, this looks like a key schema like: <code>
 * "prefix:myuser@domain.org:resources": {
 * "resourceName1": "[serialized json of resourceName1]",
 * "resourceName2": "[serialized json of resourceName2]"
 * }
 * </code> Additionally, a helper key, called the "all users" key, maintains a set of all usernames.
 *
 * <p>It's important to note that gets and puts are not symmetrical by design. That is, what you put
 * in will likely not be exactly what you get out. That's because of "unrestricted" resources, which
 * are added to the returned UserPermission.
 */
@Slf4j
public class RedisPermissionsRepository implements PermissionsRepository {

  private static final String REDIS_READ_RETRY = "permissionsRepositoryRedisRead";

  private static final String KEY_PERMISSIONS = "permissions";
  private static final String KEY_PERMISSIONS_V2 = "permissions-v2";
  private static final String KEY_ROLES = "roles";
  private static final String KEY_ALL_USERS = "users";
  private static final String KEY_ADMIN = "admin";
  private static final String KEY_ACCOUNT_MANAGERS = "accountmanagers";
  private static final String KEY_LAST_MODIFIED = "last_modified";

  private static final String UNRESTRICTED = UnrestrictedResourceConfig.UNRESTRICTED_USERNAME;
  private static final String NO_LAST_MODIFIED = "unknown_last_modified";

  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final RedisClientDelegate redisClientDelegate;
  private final List<Resource> resources;
  private final RedisPermissionRepositoryConfigProps configProps;
  private final RetryRegistry retryRegistry;
  private final AtomicReference<String> fallbackLastModified = new AtomicReference<>(null);
  private final LZ4CompressorWithLength lz4Compressor;
  private final LZ4DecompressorWithLength lz4Decompressor;

  private final LoadingCache<String, UserPermission> unrestrictedPermission =
      Caffeine.newBuilder()
          .expireAfterAccess(Duration.ofSeconds(10))
          .build(this::reloadUnrestricted);

  private final String prefix;
  private final byte[] allUsersKey;
  private final byte[] adminKey;
  private final byte[] accountManagersKey;

  private final ForkJoinPool syncThreadPool;

  RedisPermissionsRepository(
      Clock clock,
      ObjectMapper objectMapper,
      RedisClientDelegate redisClientDelegate,
      List<Resource> resources,
      RedisPermissionRepositoryConfigProps configProps,
      RetryRegistry retryRegistry) {
    this.clock = clock;
    this.objectMapper = objectMapper;
    this.redisClientDelegate = redisClientDelegate;
    this.configProps = configProps;
    this.prefix = configProps.getPrefix();
    this.resources = resources;
    this.retryRegistry = retryRegistry;

    LZ4Factory factory = LZ4Factory.fastestInstance();
    this.lz4Compressor = new LZ4CompressorWithLength(factory.fastCompressor());
    this.lz4Decompressor = new LZ4DecompressorWithLength(factory.fastDecompressor());

    this.allUsersKey = SafeEncoder.encode(String.format("%s:%s", prefix, KEY_ALL_USERS));
    this.adminKey =
        SafeEncoder.encode(String.format("%s:%s:%s", prefix, KEY_PERMISSIONS, KEY_ADMIN));
    this.accountManagersKey =
        SafeEncoder.encode(String.format("%s:%s", prefix, KEY_ACCOUNT_MANAGERS));

    this.syncThreadPool = new ForkJoinPool(configProps.getRepository().getSyncThreads());
  }

  public RedisPermissionsRepository(
      ObjectMapper objectMapper,
      RedisClientDelegate redisClientDelegate,
      List<Resource> resources,
      RedisPermissionRepositoryConfigProps configProps,
      RetryRegistry retryRegistry) {
    this(
        Clock.systemUTC(),
        objectMapper,
        redisClientDelegate,
        resources,
        configProps,
        retryRegistry);
  }

  private UserPermission reloadUnrestricted(String cacheKey) {
    return getFromRedis(UNRESTRICTED)
        .map(
            p -> {
              log.debug("reloaded user {} for key {} as {}", UNRESTRICTED, cacheKey, p);
              return p;
            })
        .orElseThrow(
            () -> {
              log.error(
                  "loading user {} for key {} failed, no permissions returned",
                  UNRESTRICTED,
                  cacheKey);
              return new PermissionRepositoryException("Failed to read unrestricted user");
            });
  }

  private UserPermission getUnrestrictedUserPermission() {
    String serverLastModified = NO_LAST_MODIFIED;
    byte[] bServerLastModified =
        redisRead(
            new TimeoutContext(
                "checkLastModified",
                clock,
                configProps.getRepository().getCheckLastModifiedTimeout()),
            c -> c.get(SafeEncoder.encode(unrestrictedLastModifiedKey())));
    if (bServerLastModified == null || bServerLastModified.length == 0) {
      log.debug(
          "no last modified time available in redis for user {} using default of {}",
          UNRESTRICTED,
          NO_LAST_MODIFIED);
    } else {
      serverLastModified = SafeEncoder.encode(bServerLastModified);
    }

    try {
      UserPermission userPermission = unrestrictedPermission.get(serverLastModified);
      if (userPermission != null && !serverLastModified.equals(NO_LAST_MODIFIED)) {
        fallbackLastModified.set(serverLastModified);
      }
      return userPermission;
    } catch (Throwable ex) {
      log.error(
          "failed reading user {} from cache for key {}", UNRESTRICTED, serverLastModified, ex);
      String fallback = fallbackLastModified.get();
      if (fallback != null) {
        UserPermission fallbackPermission = unrestrictedPermission.getIfPresent(fallback);
        if (fallbackPermission != null) {
          log.warn(
              "serving fallback permission for user {} from key {} as {}",
              UNRESTRICTED,
              fallback,
              fallbackPermission);
          return fallbackPermission;
        }
        log.warn("no fallback entry remaining in cache for key {}", fallback);
      }
      if (ex instanceof RuntimeException) {
        throw (RuntimeException) ex;
      }
      throw new IntegrationException(ex);
    }
  }

  private static class PutUpdateData {
    public byte[] userResourceKey;
    public byte[] compressedData;
  }

  @Override
  public RedisPermissionsRepository put(@NonNull UserPermission permission) {
    String userId = permission.getId();
    byte[] bUserId = SafeEncoder.encode(userId);
    List<ResourceType> resourceTypes =
        resources.stream().map(Resource::getResourceType).collect(Collectors.toList());
    Map<ResourceType, Map<String, Resource>> resourceTypeToRedisValue =
        new HashMap<>(resourceTypes.size());

    permission
        .getAllResources()
        .forEach(
            resource -> {
              resourceTypeToRedisValue
                  .computeIfAbsent(resource.getResourceType(), key -> new HashMap<>())
                  .put(resource.getName(), resource);
            });

    try {
      Set<Role> existingRoles = new HashSet<>(getUserRoleMapFromRedis(userId).values());

      // These updates are pre-prepared to reduce work done during the multi-key pipeline
      List<PutUpdateData> updateData = new ArrayList<>();
      for (ResourceType rt : resourceTypes) {
        Map<String, Resource> redisValue = resourceTypeToRedisValue.get(rt);
        byte[] userResourceKey = userKey(userId, rt);
        PutUpdateData pud = new PutUpdateData();
        pud.userResourceKey = userResourceKey;

        if (redisValue == null || redisValue.size() == 0) {
          pud.compressedData = null;
        } else {
          pud.compressedData = lz4Compressor.compress(objectMapper.writeValueAsBytes(redisValue));
        }

        updateData.add(pud);
      }

      AtomicReference<Response<List<String>>> serverTime = new AtomicReference<>();
      redisClientDelegate.withMultiKeyPipeline(
          pipeline -> {
            if (permission.isAdmin()) {
              pipeline.sadd(adminKey, bUserId);
            } else {
              pipeline.srem(adminKey, bUserId);
            }

            if (permission.isAccountManager()) {
              pipeline.sadd(accountManagersKey, bUserId);
            } else {
              pipeline.srem(accountManagersKey, bUserId);
            }

            permission.getRoles().forEach(role -> pipeline.sadd(roleKey(role), bUserId));
            existingRoles.stream()
                .filter(it -> !permission.getRoles().contains(it))
                .forEach(role -> pipeline.srem(roleKey(role), bUserId));

            for (PutUpdateData pud : updateData) {
              if (pud.compressedData == null) {
                pipeline.del(pud.userResourceKey);
              } else {
                byte[] tempKey = SafeEncoder.encode(UUID.randomUUID().toString());
                pipeline.set(tempKey, pud.compressedData);
                pipeline.rename(tempKey, pud.userResourceKey);
              }
            }

            serverTime.set(pipeline.time());
            pipeline.sadd(allUsersKey, bUserId);

            pipeline.sync();
          });
      if (UNRESTRICTED.equals(userId)) {
        String lastModified = serverTime.get().get().get(0);
        redisClientDelegate.withCommandsClient(
            c -> {
              log.debug("set last modified for user {} to {}", UNRESTRICTED, lastModified);
              c.set(unrestrictedLastModifiedKey(), lastModified);
            });
      }
    } catch (Exception e) {
      log.error("Storage exception writing {} entry.", userId, e);
    }
    return this;
  }

  @Override
  public void putAllById(Map<String, UserPermission> permissions) {
    if (permissions == null || permissions.values() == null) {
      return;
    }

    for (UserPermission permission : permissions.values()) {
      put(permission);
    }
  }

  @Override
  public Optional<UserPermission> get(@NonNull String id) {
    if (UNRESTRICTED.equals(id)) {
      return Optional.of(getUnrestrictedUserPermission());
    }
    return getFromRedis(id);
  }

  private byte[] getUserResourceBytesFromRedis(String id, ResourceType resourceType) {
    TimeoutContext timeoutContext =
        new TimeoutContext(
            String.format("get user resource from redis: %s (%s)", id, resourceType),
            clock,
            configProps.getRepository().getGetUserResourceTimeout());
    byte[] key = userKey(id, resourceType);

    byte[] compressedData =
        redisRead(timeoutContext, (ThrowingFunction<JedisBinaryCommands, byte[]>) c -> c.get(key));

    if (compressedData == null || compressedData.length == 0) {
      return null;
    }

    return lz4Decompressor.decompress(compressedData);
  }

  private Map<String, Resource> getUserResourceMapFromRedis(String id, ResourceType resourceType)
      throws IOException {
    byte[] redisData = getUserResourceBytesFromRedis(id, resourceType);

    if (redisData == null) {
      return new HashMap<>();
    }

    Class<? extends Resource> modelClazz =
        resources.stream()
            .filter(resource -> resource.getResourceType().equals(resourceType))
            .findFirst()
            .orElseThrow(IllegalArgumentException::new)
            .getClass();

    return objectMapper.readerForMapOf(modelClazz).readValue(redisData);
  }

  private Map<String, Role> getUserRoleMapFromRedis(String id) throws IOException {
    byte[] redisData = getUserResourceBytesFromRedis(id, ResourceType.ROLE);

    if (redisData == null) {
      return new HashMap<>();
    }

    return objectMapper.readValue(redisData, new TypeReference<>() {});
  }

  private Optional<UserPermission> getFromRedis(@NonNull String id) {
    try {
      TimeoutContext timeoutContext =
          new TimeoutContext(
              String.format("getPermission for user: %s", id),
              clock,
              configProps.getRepository().getGetPermissionTimeout());
      boolean userExists =
          UNRESTRICTED.equals(id)
              || redisRead(timeoutContext, c -> c.sismember(allUsersKey, SafeEncoder.encode(id)));
      if (!userExists) {
        log.debug("request for user {} not found in redis", id);
        return Optional.empty();
      }
      UserPermission userPermission = new UserPermission().setId(id);

      for (Resource r : resources) {
        ResourceType resourceType = r.getResourceType();
        Map<String, Resource> resourcePermissions = getUserResourceMapFromRedis(id, resourceType);

        if (resourcePermissions != null && !resourcePermissions.isEmpty()) {
          userPermission.addResources(resourcePermissions.values());
        }
      }

      if (!UNRESTRICTED.equals(id)) {
        userPermission.setAdmin(
            redisRead(timeoutContext, c -> c.sismember(adminKey, SafeEncoder.encode(id))));
        userPermission.setAccountManager(
            redisRead(
                timeoutContext, c -> c.sismember(accountManagersKey, SafeEncoder.encode(id))));
        userPermission.merge(getUnrestrictedUserPermission());
      }
      return Optional.of(userPermission);
    } catch (Throwable t) {
      String message = String.format("Storage exception reading %s entry.", id);
      log.error(message, t);
      if (t instanceof SpinnakerException) {
        throw (SpinnakerException) t;
      }
      throw new PermissionReadException(message, t);
    }
  }

  @Override
  public Map<String, Set<Role>> getAllById() {
    Set<String> allUsers =
        scanSet(allUsersKey).stream().map(String::toLowerCase).collect(Collectors.toSet());

    return getRolesOf(allUsers);
  }

  @Override
  public Map<String, Set<Role>> getAllByRoles(List<String> anyRoles) {
    if (anyRoles == null) {
      return getAllById();
    } else if (anyRoles.isEmpty()) {
      return getRolesOf(Set.of(UNRESTRICTED));
    }

    Set<String> uniqueUsernames = new HashSet<>();

    try {
      uniqueUsernames =
          syncThreadPool
              .submit(
                  () ->
                      new HashSet<>(anyRoles)
                          .parallelStream()
                              .flatMap(
                                  role -> scanSet(roleKey(role)).stream().map(String::toLowerCase))
                              .collect(Collectors.toSet()))
              .get();
    } catch (ExecutionException e) {
      log.error("Execution exception reading usernames for roles", e);
    } catch (InterruptedException e) {
      log.error("Interrupted exception reading usernames for roles", e);
    }

    uniqueUsernames.add(UNRESTRICTED);

    return getRolesOf(uniqueUsernames);
  }

  @Override
  public void remove(@NonNull String id) {
    try {
      Map<String, Role> userRolesById = getUserRoleMapFromRedis(id);
      byte[] bId = SafeEncoder.encode(id);

      redisClientDelegate.withMultiKeyPipeline(
          p -> {
            p.srem(allUsersKey, bId);
            userRolesById.keySet().forEach(roleName -> p.srem(roleKey(roleName), bId));

            resources.stream().map(Resource::getResourceType).forEach(r -> p.del(userKey(id, r)));
            p.srem(adminKey, bId);
            p.srem(accountManagersKey, bId);
            p.sync();
          });
    } catch (Exception e) {
      log.error("Storage exception reading " + id + " entry.", e);
    }
  }

  private Map<String, Set<Role>> getRolesOf(Set<String> userIds) {
    if (userIds.isEmpty()) {
      return new HashMap<>(0);
    }

    Map<String, Set<Role>> usersToRoles = new HashMap<>(userIds.size());

    for (String userId : userIds) {
      try {
        Set<Role> roles =
            getUserResourceMapFromRedis(userId, ResourceType.ROLE).values().stream()
                .map(it -> (Role) it)
                .collect(Collectors.toSet());
        usersToRoles.put(userId, roles);
      } catch (Throwable t) {
        String message = String.format("Storage exception reading %s entry.", userId);
        log.error(message, t);
        if (t instanceof SpinnakerException) {
          throw (SpinnakerException) t;
        }
        throw new PermissionReadException(message, t);
      }
    }

    return usersToRoles;
  }

  private Set<String> scanSet(byte[] key) {
    return redisClientDelegate
        .withBinaryClient(
            jedis -> {
              return jedis.smembers(key);
            })
        .stream()
        .map(SafeEncoder::encode)
        .collect(Collectors.toSet());
  }

  private byte[] userKey(String userId, ResourceType r) {
    return SafeEncoder.encode(
        String.format("%s:%s:%s:%s", prefix, KEY_PERMISSIONS_V2, userId, r.keySuffix()));
  }

  private byte[] roleKey(Role role) {
    return roleKey(role.getName());
  }

  private byte[] roleKey(String role) {
    return SafeEncoder.encode(String.format("%s:%s:%s", prefix, KEY_ROLES, role));
  }

  private String lastModifiedKey(String userId) {
    return String.format("%s:%s:%s", prefix, KEY_LAST_MODIFIED, userId);
  }

  private String unrestrictedLastModifiedKey() {
    return lastModifiedKey(UNRESTRICTED);
  }

  /** Used to swallow checked exceptions from Jackson methods. */
  @FunctionalInterface
  private interface ThrowingFunction<T, R> extends Function<T, R> {

    @Override
    default R apply(T t) {
      try {
        return applyThrows(t);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    R applyThrows(T t) throws Exception;
  }

  /**
   * TimeoutContext allows specifying an expiration time for request processing.
   *
   * <p>If something exceeds the specified timeout duration, the request handler should stop doing
   * work and just bail out with a timeout.
   */
  private static class TimeoutContext {
    private final String name;
    private final Instant expiry;
    private final Clock clock;
    private final Duration timeout;

    public TimeoutContext(String name, Clock clock, Duration timeout) {
      this(name, clock, timeout, Instant.now(clock));
    }

    public TimeoutContext(String name, Clock clock, Duration timeout, Instant startTime) {
      this.name = name;
      this.expiry = startTime.plus(timeout);
      this.clock = clock;
      this.timeout = timeout;
    }

    boolean isTimedOut() {
      return Instant.now(clock).isAfter(expiry);
    }

    String getName() {
      return name;
    }

    Duration getTimeout() {
      return timeout;
    }
  }

  private <T> T redisRead(TimeoutContext timeoutContext, Function<JedisBinaryCommands, T> fn) {
    return retryRegistry
        .retry(REDIS_READ_RETRY)
        .executeSupplier(
            () -> {
              if (timeoutContext.isTimedOut()) {
                throw new PermissionReadException(
                    String.format(
                        "request processing timeout after %s for %s",
                        timeoutContext.getTimeout(), timeoutContext.getName()));
              }
              return redisClientDelegate.withBinaryClient(fn);
            });
  }
}
