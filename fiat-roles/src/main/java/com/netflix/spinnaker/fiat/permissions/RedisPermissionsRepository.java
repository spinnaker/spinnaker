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
import com.google.common.collect.Table;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.redis.JedisSource;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This Redis-backed permission repository is structured in a way to optimized reading types of
 * resource permissions. In general, this looks like a key schema like:
 * <code>
 * "prefix:myuser@domain.org:resources": {
 * "resourceName1": "[serialized json of resourceName1]",
 * "resourceName2": "[serialized json of resourceName2]"
 * }
 * </code>
 * Additionally, a helper key, called the "all users" key, maintains a set of all usernames.
 * <p>
 * It's important to note that gets and puts are not symmetrical by design. That is, what you put in
 * will likely not be exactly what you get out. That's because of "unrestricted" resources, which
 * are added to the returned UserPermission.
 */
// TODO(ttomsu): Add RedisCacheOptions from Clouddriver.
@Component
@Slf4j
public class RedisPermissionsRepository implements PermissionsRepository {

  private static final String KEY_PERMISSIONS = "permissions";
  private static final String KEY_ROLES = "roles";
  private static final String KEY_ALL_USERS = "users";

  private static final String UNRESTRICTED = UnrestrictedResourceConfig.UNRESTRICTED_USERNAME;

  @Value("${fiat.redis.prefix:spinnaker:fiat}")
  @Setter
  private String prefix;

  @Autowired
  @Setter
  private ObjectMapper objectMapper;

  @Autowired
  @Setter
  private JedisSource jedisSource;

  @Override
  public RedisPermissionsRepository put(@NonNull UserPermission permission) {
    Map<ResourceType, Map<String, String>> resourceTypeToRedisValue =
        new HashMap<>(ResourceType.values().length);

    permission
        .getAllResources()
        .forEach(resource -> {
          try {
            resourceTypeToRedisValue
                .computeIfAbsent(resource.getResourceType(), key -> new HashMap<>())
                .put(resource.getName(), objectMapper.writeValueAsString(resource));
          } catch (JsonProcessingException jpe) {
            log.error("Serialization exception writing " + permission.getId() + " entry.", jpe);
          }
        });

    try (Jedis jedis = jedisSource.getJedis()) {
      Pipeline deleteOldValuesPipeline = jedis.pipelined();
      Pipeline insertNewValuesPipeline = jedis.pipelined();

      String userId = permission.getId();
      insertNewValuesPipeline.sadd(allUsersKey(), userId);

      permission.getRoles().forEach(role -> insertNewValuesPipeline.sadd(roleKey(role), userId));

      for (ResourceType r : ResourceType.values()) {
        String userResourceKey = userKey(userId, r);

        deleteOldValuesPipeline.del(userResourceKey);

        Map<String, String> redisValue = resourceTypeToRedisValue.get(r);
        if (redisValue != null && !redisValue.isEmpty()) {
          insertNewValuesPipeline.hmset(userResourceKey, redisValue);
        }
      }
      deleteOldValuesPipeline.sync();
      insertNewValuesPipeline.sync();
    } catch (Exception e) {
      log.error("Storage exception writing " + permission.getId() + " entry.", e);
    }
    return this;
  }

  @Override
  public Optional<UserPermission> get(@NonNull String id) {
    UserPermission userPermission = null;
    try (Jedis jedis = jedisSource.getJedis()) {
      RawUserPermission userResponseMap = new RawUserPermission();
      RawUserPermission unrestrictedResponseMap = new RawUserPermission();

      Pipeline p = jedis.pipelined();
      for (ResourceType r : ResourceType.values()) {
        Response<Map<String, String>> resourceMap = p.hgetAll(userKey(id, r));
        userResponseMap.put(r, resourceMap);
        Response<Map<String, String>> unrestrictedMap = p.hgetAll(unrestrictedUserKey(r));
        unrestrictedResponseMap.put(r, unrestrictedMap);
      }
      p.sync();

      UserPermission unrestrictedUser = getUserPermission(UNRESTRICTED, unrestrictedResponseMap);
      userPermission = getUserPermission(id, userResponseMap).merge(unrestrictedUser);
    } catch (Exception e) {
      log.error("Storage exception reading " + id + " entry.", e);
    }
    return userPermission == null ? Optional.empty() : Optional.of(userPermission);
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

    for (String userId : responseTable.rowKeySet()) {
      RawUserPermission rawUser = new RawUserPermission(responseTable.row(userId));
      UserPermission permission = getUserPermission(userId, rawUser);
      allById.put(userId, permission.merge(unrestrictedUser));
    }
    return allById;
  }

  private UserPermission getUserPermission(String userId, RawUserPermission raw) {

    UserPermission permission = new UserPermission().setId(userId);

    for (Map.Entry<ResourceType, Response<Map<String, String>>> entry : raw.entrySet()) {
      ResourceType r = entry.getKey();

      Map<String /*resourceName*/, String /*resource json*/> resourceMap = entry.getValue().get();
      permission.addResources(extractResources(r, resourceMap));
    }

    return permission;
  }

  private Table<String, ResourceType, Response<Map<String, String>>> getAllFromRedis() {
    try (Jedis jedis = jedisSource.getJedis()) {
      Set<String> allUserIds = jedis.smembers(allUsersKey());

      if (allUserIds.size() == 0) {
        return HashBasedTable.create();
      }
      Table<String, ResourceType, Response<Map<String, String>>> responseTable =
          ArrayTable.create(allUserIds, new ArrayIterator<>(ResourceType.values()));

      Pipeline p = jedis.pipelined();
      for (String userId : allUserIds) {
        for (ResourceType r : ResourceType.values()) {
          responseTable.put(userId, r, p.hgetAll(userKey(userId, r)));
        }
      }
      p.sync();
      return responseTable;
    } catch (Exception e) {
      log.error("Storage exception reading all entries.", e);
    }
    return null;
  }

  @Override
  public void remove(@NonNull String id) {
    try (Jedis jedis = jedisSource.getJedis()) {
      Map<String, String> userRolesById = jedis.hgetAll(userKey(id, ResourceType.ROLE));

      Pipeline p = jedis.pipelined();

      p.srem(allUsersKey(), id);
      for (String roleName : userRolesById.keySet()) {
        p.srem(roleKey(roleName), id);
      }

      for (ResourceType r : ResourceType.values()) {
        p.del(userKey(id, r));
      }
      p.sync();
    } catch (Exception e) {
      log.error("Storage exception reading " + id + " entry.", e);
    }
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

  private String roleKey(Role role) {
    return roleKey(role.getName());
  }

  private String roleKey(String role) {
    return String.format("%s:%s:%s", prefix, KEY_ROLES, role);
  }

  private Set<Resource> extractResources(ResourceType r, Map<String, String> resourceMap) {
    return resourceMap
        .values()
        .stream()
        .map((ThrowingFunction<String, ? extends Resource>) serialized ->
            objectMapper.readValue(serialized, r.modelClass))
        .collect(Collectors.toSet());
  }

  /**
   * Used to swallow checked exceptions from Jackson methods.
   */
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

    RawUserPermission() {
      super();
    }

    RawUserPermission(Map<ResourceType, Response<Map<String, String>>> source) {
      super(source);
    }
  }
}
