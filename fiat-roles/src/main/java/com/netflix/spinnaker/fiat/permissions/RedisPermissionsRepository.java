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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ArrayIterator;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import redis.clients.jedis.Response;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.commands.JedisCommands;

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
  private static final String KEY_ROLES = "roles";
  private static final String KEY_ALL_USERS = "users";
  private static final String KEY_ADMIN = "admin";
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

  private final LoadingCache<String, UserPermission> unrestrictedPermission =
      Caffeine.newBuilder()
          .expireAfterAccess(Duration.ofSeconds(10))
          .build(k -> reloadUnrestricted(k));

  private final String prefix;

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
    String serverLastModified =
        redisRead(
            new TimeoutContext(
                "checkLastModified",
                clock,
                configProps.getRepository().getCheckLastModifiedTimeout()),
            c -> c.get(unrestrictedLastModifiedKey()));
    if (serverLastModified == null || serverLastModified.isEmpty()) {
      log.debug(
          "no last modified time available in redis for user {} using default of {}",
          UNRESTRICTED,
          NO_LAST_MODIFIED);
      serverLastModified = NO_LAST_MODIFIED;
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

  @Override
  public RedisPermissionsRepository put(@NonNull UserPermission permission) {
    val resourceTypes =
        resources.stream().map(Resource::getResourceType).collect(Collectors.toList());
    Map<ResourceType, Map<String, String>> resourceTypeToRedisValue =
        new HashMap<>(resourceTypes.size());

    permission
        .getAllResources()
        .forEach(
            resource -> {
              try {
                resourceTypeToRedisValue
                    .computeIfAbsent(resource.getResourceType(), key -> new HashMap<>())
                    .put(resource.getName(), objectMapper.writeValueAsString(resource));
              } catch (JsonProcessingException jpe) {
                log.error("Serialization exception writing {} entry.", permission.getId(), jpe);
              }
            });

    try {
      Set<Role> existingRoles =
          redisClientDelegate.withCommandsClient(
              client -> {
                return client
                    .hgetAll(userKey(permission.getId(), ResourceType.ROLE))
                    .values()
                    .stream()
                    .map(
                        (ThrowingFunction<String, Role>)
                            serialized -> objectMapper.readValue(serialized, Role.class))
                    .collect(Collectors.toSet());
              });

      AtomicReference<Response<List<String>>> serverTime = new AtomicReference<>();
      redisClientDelegate.withMultiKeyPipeline(
          pipeline -> {
            String userId = permission.getId();
            if (permission.isAdmin()) {
              pipeline.sadd(adminKey(), userId);
            } else {
              pipeline.srem(adminKey(), userId);
            }

            permission.getRoles().forEach(role -> pipeline.sadd(roleKey(role), userId));
            existingRoles.stream()
                .filter(it -> !permission.getRoles().contains(it))
                .forEach(role -> pipeline.srem(roleKey(role), userId));

            resources.stream()
                .map(Resource::getResourceType)
                .forEach(
                    r -> {
                      String userResourceKey = userKey(userId, r);
                      Map<String, String> redisValue = resourceTypeToRedisValue.get(r);
                      String tempKey = UUID.randomUUID().toString();
                      if (redisValue != null && !redisValue.isEmpty()) {
                        pipeline.hmset(tempKey, redisValue);
                        pipeline.rename(tempKey, userResourceKey);
                      } else {
                        pipeline.del(userResourceKey);
                      }
                    });

            serverTime.set(pipeline.time());
            pipeline.sadd(allUsersKey(), userId);

            pipeline.sync();
          });
      if (UNRESTRICTED.equals(permission.getId())) {
        String lastModified = serverTime.get().get().get(0);
        redisClientDelegate.withCommandsClient(
            c -> {
              log.debug("set last modified for user {} to {}", UNRESTRICTED, lastModified);
              c.set(unrestrictedLastModifiedKey(), lastModified);
            });
      }
    } catch (Exception e) {
      log.error("Storage exception writing {} entry.", permission.getId(), e);
    }
    return this;
  }

  @Override
  public Optional<UserPermission> get(@NonNull String id) {
    if (UNRESTRICTED.equals(id)) {
      return Optional.of(getUnrestrictedUserPermission());
    }
    return getFromRedis(id);
  }

  private Optional<UserPermission> getFromRedis(@NonNull String id) {
    try {
      TimeoutContext timeoutContext =
          new TimeoutContext(
              String.format("getPermission for user: %s", id),
              clock,
              configProps.getRepository().getGetPermissionTimeout());
      boolean userExists =
          UNRESTRICTED.equals(id) || redisRead(timeoutContext, c -> c.sismember(allUsersKey(), id));
      if (!userExists) {
        log.debug("request for user {} not found in redis", id);
        return Optional.empty();
      }
      UserPermission userPermission = new UserPermission().setId(id);
      resources.forEach(
          r -> {
            ResourceType resourceType = r.getResourceType();
            String userKey = userKey(id, resourceType);
            Map<String, String> resourcePermissions = hgetall(timeoutContext, userKey);
            userPermission.addResources(extractResources(resourceType, resourcePermissions));
          });
      if (!UNRESTRICTED.equals(id)) {
        userPermission.setAdmin(redisRead(timeoutContext, c -> c.sismember(adminKey(), id)));
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
  public Map<String, UserPermission> getAllById() {
    Table<String, ResourceType, Response<Map<String, String>>> responseTable = getAllFromRedis();
    if (responseTable == null) {
      return new HashMap<>(0);
    }

    Map<String, UserPermission> allById = new HashMap<>(responseTable.rowKeySet().size());

    RawUserPermission rawUnrestricted = new RawUserPermission(responseTable.row(UNRESTRICTED));
    UserPermission unrestrictedUser = getUserPermission(UNRESTRICTED, rawUnrestricted);
    Set<String> adminSet = getAllAdmins();

    for (String userId : responseTable.rowKeySet()) {
      RawUserPermission rawUser = new RawUserPermission(responseTable.row(userId));
      rawUser.isAdmin = adminSet.contains(userId);
      UserPermission permission = getUserPermission(userId, rawUser);
      allById.put(userId, permission.merge(unrestrictedUser));
    }
    return allById;
  }

  @Override
  public Map<String, UserPermission> getAllByRoles(List<String> anyRoles) {
    if (anyRoles == null) {
      return getAllById();
    } else if (anyRoles.isEmpty()) {
      val unrestricted = getFromRedis(UNRESTRICTED);
      if (unrestricted.isPresent()) {
        val map = new HashMap<String, UserPermission>();
        map.put(UNRESTRICTED, unrestricted.get());
        return map;
      }
      return new HashMap<>();
    }

    final Set<String> dedupedUsernames = new HashSet<>();
    for (String role : new HashSet<>(anyRoles)) {
      dedupedUsernames.addAll(scanSet(roleKey(role)));
    }
    dedupedUsernames.add(UNRESTRICTED);

    Table<String, ResourceType, Response<Map<String, String>>> responseTable =
        getAllFromRedis(dedupedUsernames);
    if (responseTable == null) {
      return new HashMap<>(0);
    }

    RawUserPermission rawUnrestricted = new RawUserPermission(responseTable.row(UNRESTRICTED));
    UserPermission unrestrictedUser = getUserPermission(UNRESTRICTED, rawUnrestricted);
    Set<String> adminSet = getAllAdmins();

    return dedupedUsernames.stream()
        .map(
            userId -> {
              RawUserPermission rawUser = new RawUserPermission(responseTable.row(userId));
              rawUser.isAdmin = adminSet.contains(userId);
              return getUserPermission(userId, rawUser);
            })
        .collect(
            Collectors.toMap(
                UserPermission::getId, permission -> permission.merge(unrestrictedUser)));
  }

  private UserPermission getUserPermission(String userId, RawUserPermission raw) {

    UserPermission permission = new UserPermission().setId(userId);

    for (Map.Entry<ResourceType, Response<Map<String, String>>> entry : raw.entrySet()) {
      ResourceType r = entry.getKey();

      Map<String /*resourceName*/, String /*resource json*/> resourceMap = entry.getValue().get();
      permission.addResources(extractResources(r, resourceMap));
    }
    permission.setAdmin(raw.isAdmin);

    return permission;
  }

  private Table<String, ResourceType, Response<Map<String, String>>> getAllFromRedis() {
    try {
      Set<String> allUsers = scanSet(allUsersKey());
      return getAllFromRedis(allUsers);
    } catch (Exception e) {
      log.error("Storage exception reading all entries.", e);
      return null;
    }
  }

  private Table<String, ResourceType, Response<Map<String, String>>> getAllFromRedis(
      Set<String> userIds) {
    if (userIds.size() == 0) {
      return HashBasedTable.create();
    }

    try {
      final Table<String, ResourceType, Response<Map<String, String>>> responseTable =
          ArrayTable.create(
              userIds,
              new ArrayIterator<>(
                  resources.stream().map(Resource::getResourceType).toArray(ResourceType[]::new)));
      for (List<String> userIdSubset : Lists.partition(new ArrayList<>(userIds), 10)) {
        redisClientDelegate.withMultiKeyPipeline(
            p -> {
              for (String userId : userIdSubset) {
                resources.stream()
                    .map(Resource::getResourceType)
                    .forEach(
                        r -> {
                          responseTable.put(userId, r, p.hgetAll(userKey(userId, r)));
                        });
              }
              p.sync();
            });
      }
      return responseTable;
    } catch (Exception e) {
      log.error("Storage exception reading all entries.", e);
    }
    return null;
  }

  @Override
  public void remove(@NonNull String id) {
    try {
      Map<String, String> userRolesById =
          redisClientDelegate.withCommandsClient(
              jedis -> {
                return jedis.hgetAll(userKey(id, ResourceType.ROLE));
              });

      redisClientDelegate.withMultiKeyPipeline(
          p -> {
            p.srem(allUsersKey(), id);
            for (String roleName : userRolesById.keySet()) {
              p.srem(roleKey(roleName), id);
            }

            resources.stream()
                .map(Resource::getResourceType)
                .forEach(
                    r -> {
                      p.del(userKey(id, r));
                    });
            p.srem(adminKey(), id);
            p.sync();
          });
    } catch (Exception e) {
      log.error("Storage exception reading " + id + " entry.", e);
    }
  }

  private Set<String> scanSet(String key) {
    final Set<String> results = new HashSet<>();
    final AtomicReference<String> cursor = new AtomicReference<>(ScanParams.SCAN_POINTER_START);
    do {
      final ScanResult<String> result =
          redisClientDelegate.withCommandsClient(
              jedis -> {
                return jedis.sscan(key, cursor.get());
              });
      results.addAll(result.getResult());
      cursor.set(result.getCursor());
    } while (!"0".equals(cursor.get()));
    return results;
  }

  private Set<String> getAllAdmins() {
    return scanSet(adminKey());
  }

  private String allUsersKey() {
    return String.format("%s:%s", prefix, KEY_ALL_USERS);
  }

  private String unrestrictedUserKey(ResourceType r) {
    return userKey(UNRESTRICTED, r);
  }

  private String userKey(String userId, ResourceType r) {
    return String.format("%s:%s:%s:%s", prefix, KEY_PERMISSIONS, userId, r.keySuffix());
  }

  private String adminKey() {
    return String.format("%s:%s:%s", prefix, KEY_PERMISSIONS, KEY_ADMIN);
  }

  private String roleKey(Role role) {
    return roleKey(role.getName());
  }

  private String roleKey(String role) {
    return String.format("%s:%s:%s", prefix, KEY_ROLES, role);
  }

  private String lastModifiedKey(String userId) {
    return String.format("%s:%s:%s", prefix, KEY_LAST_MODIFIED, userId);
  }

  private String unrestrictedLastModifiedKey() {
    return lastModifiedKey(UNRESTRICTED);
  }

  private Set<Resource> extractResources(ResourceType r, Map<String, String> resourceMap) {
    val modelClazz =
        resources.stream()
            .filter(resource -> resource.getResourceType().equals(r))
            .findFirst()
            .orElseThrow(IllegalArgumentException::new)
            .getClass();
    return resourceMap.values().stream()
        .map(
            (ThrowingFunction<String, ? extends Resource>)
                serialized -> objectMapper.readValue(serialized, modelClazz))
        .collect(Collectors.toSet());
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

  private class RawUserPermission extends HashMap<ResourceType, Response<Map<String, String>>> {

    private boolean isAdmin = false;

    RawUserPermission() {
      super();
    }

    RawUserPermission(Map<ResourceType, Response<Map<String, String>>> source) {
      super(source);
    }
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

  private <T> T redisRead(TimeoutContext timeoutContext, Function<JedisCommands, T> fn) {
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
              return redisClientDelegate.withCommandsClient(fn);
            });
  }

  private Map<String, String> hgetall(TimeoutContext timeoutContext, String key) {
    Map<String, String> all = new HashMap<>();
    String cursor = ScanParams.SCAN_POINTER_START;
    do {
      final String thisCursor = cursor;
      ScanResult<Map.Entry<String, String>> result =
          redisRead(timeoutContext, c -> c.hscan(key, thisCursor));
      result.getResult().forEach(e -> all.put(e.getKey(), e.getValue()));
      cursor = result.getCursor();
    } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
    return all;
  }
}
