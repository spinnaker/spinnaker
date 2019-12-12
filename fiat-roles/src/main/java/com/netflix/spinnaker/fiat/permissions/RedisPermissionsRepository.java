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
import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Response;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

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
@Component
@Slf4j
public class RedisPermissionsRepository implements PermissionsRepository {

  private static final String KEY_PERMISSIONS = "permissions";
  private static final String KEY_ROLES = "roles";
  private static final String KEY_ALL_USERS = "users";
  private static final String KEY_ADMIN = "admin";

  private static final String UNRESTRICTED = UnrestrictedResourceConfig.UNRESTRICTED_USERNAME;

  private final ObjectMapper objectMapper;
  private final RedisClientDelegate redisClientDelegate;

  private final String prefix;

  @Autowired
  public RedisPermissionsRepository(
      ObjectMapper objectMapper,
      RedisClientDelegate redisClientDelegate,
      @Value("${fiat.redis.prefix:spinnaker:fiat}") String prefix) {
    this.objectMapper = objectMapper;
    this.redisClientDelegate = redisClientDelegate;
    this.prefix = prefix;
  }

  @Override
  public RedisPermissionsRepository put(@NonNull UserPermission permission) {
    Map<ResourceType, Map<String, String>> resourceTypeToRedisValue =
        new HashMap<>(ResourceType.values().length);

    permission
        .getAllResources()
        .forEach(
            resource -> {
              try {
                resourceTypeToRedisValue
                    .computeIfAbsent(resource.getResourceType(), key -> new HashMap<>())
                    .put(resource.getName(), objectMapper.writeValueAsString(resource));
              } catch (JsonProcessingException jpe) {
                log.error("Serialization exception writing " + permission.getId() + " entry.", jpe);
              }
            });

    try {
      Set<Role> existingRoles =
          redisClientDelegate.withCommandsClient(
              client -> {
                return client.hgetAll(userKey(permission.getId(), ResourceType.ROLE)).values()
                    .stream()
                    .map(
                        (ThrowingFunction<String, Role>)
                            serialized -> objectMapper.readValue(serialized, Role.class))
                    .collect(Collectors.toSet());
              });

      redisClientDelegate.withMultiKeyPipeline(
          pipeline -> {
            String userId = permission.getId();
            pipeline.sadd(allUsersKey(), userId);

            if (permission.isAdmin()) {
              pipeline.sadd(adminKey(), userId);
            } else {
              pipeline.srem(adminKey(), userId);
            }

            permission.getRoles().forEach(role -> pipeline.sadd(roleKey(role), userId));
            existingRoles.stream()
                .filter(it -> !permission.getRoles().contains(it))
                .forEach(role -> pipeline.srem(roleKey(role), userId));

            for (ResourceType r : ResourceType.values()) {
              String userResourceKey = userKey(userId, r);
              Map<String, String> redisValue = resourceTypeToRedisValue.get(r);
              String tempKey = UUID.randomUUID().toString();
              if (redisValue != null && !redisValue.isEmpty()) {
                pipeline.hmset(tempKey, redisValue);
                pipeline.rename(tempKey, userResourceKey);
              } else {
                pipeline.del(userResourceKey);
              }
            }
            pipeline.sync();
          });
    } catch (Exception e) {
      log.error("Storage exception writing " + permission.getId() + " entry.", e);
    }
    return this;
  }

  @Override
  public Optional<UserPermission> get(@NonNull String id) {
    try {
      return redisClientDelegate.withMultiKeyPipeline(
          p -> {
            RawUserPermission userResponseMap = new RawUserPermission();
            RawUserPermission unrestrictedResponseMap = new RawUserPermission();

            Response<Boolean> isUserInRepo = p.sismember(allUsersKey(), id);
            for (ResourceType r : ResourceType.values()) {
              Response<Map<String, String>> resourceMap = p.hgetAll(userKey(id, r));
              userResponseMap.put(r, resourceMap);
              Response<Map<String, String>> unrestrictedMap = p.hgetAll(unrestrictedUserKey(r));
              unrestrictedResponseMap.put(r, unrestrictedMap);
              log.info("Resource: {}; map size: {}", r, unrestrictedResponseMap.size());
            }
            Response<Boolean> admin = p.sismember(adminKey(), id);
            p.sync();

            if (!isUserInRepo.get()) {
              return Optional.empty();
            }

            userResponseMap.isAdmin = admin.get();
            UserPermission unrestrictedUser =
                getUserPermission(UNRESTRICTED, unrestrictedResponseMap);
            return Optional.of(getUserPermission(id, userResponseMap).merge(unrestrictedUser));
          });
    } catch (Exception e) {
      log.error("Storage exception reading " + id + " entry.", e);
    }
    return Optional.empty();
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
      val unrestricted = get(UNRESTRICTED);
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
          ArrayTable.create(userIds, new ArrayIterator<>(ResourceType.values()));
      for (List<String> userIdSubset : Lists.partition(new ArrayList<>(userIds), 10)) {
        redisClientDelegate.withMultiKeyPipeline(
            p -> {
              for (String userId : userIdSubset) {
                for (ResourceType r : ResourceType.values()) {
                  responseTable.put(userId, r, p.hgetAll(userKey(userId, r)));
                }
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

            for (ResourceType r : ResourceType.values()) {
              p.del(userKey(id, r));
            }
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

  private Set<Resource> extractResources(ResourceType r, Map<String, String> resourceMap) {
    return resourceMap.values().stream()
        .map(
            (ThrowingFunction<String, ? extends Resource>)
                serialized -> objectMapper.readValue(serialized, r.modelClass))
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
}
