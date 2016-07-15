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
import com.google.common.collect.Table;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Named;
import com.netflix.spinnaker.fiat.model.resources.Resource;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This Redis-backed permission repository is structured in a way to optimized reading types of
 * resource permissions. In general, this looks like a key schema like:
 * <code>
 * "prefix:myuser@domain.org:resources": {
 *   "resourceName1": "[serialized json of resourceName1]",
 *   "resourceName2": "[serialized json of resourceName2]"
 * }
 * </code>
 * Additionally, a helper key, called the "all users" key, maintains a set of all usernames.
 */
@Component
@Slf4j
public class RedisPermissionsRepository implements PermissionsRepository {

  private static final String KEY_PERMISSIONS = "permissions";
  private static final String KEY_ALL_USERS = "users";

  @Value("${fiat.redis.prefix:spinnaker:fiat}")
  @Setter
  private String prefix;

  @Autowired
  @Setter
  private ObjectMapper objectMapper;

  @Autowired
  @Setter
  private JedisSource jedisSource;

  // TODO(ttomsu): Add RedisCacheOptions from Clouddriver.
  @Override
  public PermissionsRepository put(@NonNull UserPermission permission) {
    try (Jedis jedis = jedisSource.getJedis()) {
      for (Resource r : Resource.values()) {
        Map<String, String> resourceValues = new HashMap<>();

        Consumer<Named> putInValuesMap = namedResource -> {
          try {
            resourceValues.put(namedResource.getName(),
                               objectMapper.writeValueAsString(namedResource));
          } catch (JsonProcessingException jpe) {
            log.error("Serialization exception writing " + permission.getId() + " entry.", jpe);
          }
        };

        switch (r) {
          case ACCOUNT:
            permission.getAccounts().stream().forEach(putInValuesMap);
            break;
          case APPLICATION:
            permission.getApplications().stream().forEach(putInValuesMap);
            break;
        }

        String userId = permission.getId();
        String userResourceKey = userKey(userId, r);

        jedis.del(userResourceKey); // Clears any values that may have been deleted.
        if (!resourceValues.isEmpty()) {
          jedis.hmset(userResourceKey, resourceValues);
          jedis.sadd(allUsersKey(), userId);
        }
      }
    } catch (Exception e) {
      log.error("Storage exception writing " + permission.getId() + " entry.", e);
    }
    return this;
  }

  @Override
  public Optional<UserPermission> get(@NonNull String id) {
    UserPermission userPermission = new UserPermission().setId(id);
    try (Jedis jedis = jedisSource.getJedis()) {
      for (Resource r : Resource.values()) {
        Map<String, String> resourceMap = jedis.hgetAll(userKey(id, r));

        switch (r) {
          case ACCOUNT:
            userPermission.getAccounts().addAll(extractAccounts(resourceMap));
            break;
          case APPLICATION:
            userPermission.getApplications().addAll(extractApplications(resourceMap));
            break;
        }
      }
    } catch (Exception e) {
      log.error("Storage exception reading " + id + " entry.", e);
    }

    return userPermission.isEmpty() ? Optional.empty() : Optional.of(userPermission);
  }

  @Override
  public Map<String, UserPermission> getAllById() {
    Table<String, Resource, Response<Map<String, String>>> responseTable = getAllFromRedis();
    if (responseTable == null) {
      return new HashMap<>(0);
    }
    Map<String, UserPermission> allById = new HashMap<>(responseTable.rowKeySet().size());

    for (Map.Entry<String, Map<Resource, Response<Map<String, String>>>> entry1 : responseTable.rowMap().entrySet()) {
      String userId = entry1.getKey();
      for (Map.Entry<Resource, Response<Map<String, String>>> entry2 : entry1.getValue().entrySet()) {
        Resource r = entry2.getKey();
        Map<String /*resourceName*/, String /*resource json*/> resourceMap = entry2.getValue().get();

        Function<String, UserPermission> newUser = id -> new UserPermission().setId(id);

        switch (r) {
          case ACCOUNT:
            allById.computeIfAbsent(userId, newUser)
                   .getAccounts()
                   .addAll(extractAccounts(resourceMap));
            break;
          case APPLICATION:
            allById.computeIfAbsent(userId, newUser)
                   .getApplications()
                   .addAll(extractApplications(resourceMap));
        }
      }
    }
    return allById;
  }

  private Table<String, Resource, Response<Map<String, String>>> getAllFromRedis() {
    try (Jedis jedis = jedisSource.getJedis()) {
      Set<String> allUserIds = jedis.smembers(allUsersKey());

      Table<String, Resource, Response<Map<String, String>>> responseTable =
          ArrayTable.create(allUserIds, new ArrayIterator<>(Resource.values()));

      Pipeline p = jedis.pipelined();
      for (String userId : allUserIds) {
        for (Resource r : Resource.values()) {
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
    UserPermission userPermission = new UserPermission().setId(id);
    try (Jedis jedis = jedisSource.getJedis()) {
      jedis.srem(allUsersKey(), id);
      for (Resource r : Resource.values()) {
        jedis.del(userKey(id, r));
      }
    } catch (Exception e) {
      log.error("Storage exception reading " + id + " entry.", e);
    }
  }

  String allUsersKey() {
    return String.format("%s:%s", prefix, KEY_ALL_USERS);
  }

  String userKey(String userId, Resource r) {
    return String.format("%s:%s:%s:%s", prefix, KEY_PERMISSIONS, userId, r.keySuffix());
  }

  private Set<Account> extractAccounts(Map<String, String> resourceMap) {
    return resourceMap
        .values()
        .stream()
        .map((ThrowingFunction<String, Account>) serialized ->
            objectMapper.readValue(serialized, Account.class))
        .collect(Collectors.toSet());
  }

  private Set<Application> extractApplications(Map<String, String> resourceMap) {
    return resourceMap
        .values()
        .stream()
        .map((ThrowingFunction<String, Application>) serialized ->
            objectMapper.readValue(serialized, Application.class))
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
}
