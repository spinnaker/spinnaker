/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.index.config.CanaryConfigIndexAction;
import com.netflix.kayenta.security.AccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.*;

import static com.netflix.kayenta.index.CanaryConfigIndexingAgent.*;

@Slf4j
public class CanaryConfigIndex {

  private final JedisPool jedisPool;
  private final ObjectMapper kayentaObjectMapper;

  public CanaryConfigIndex(JedisPool jedisPool,
                           ObjectMapper kayentaObjectMapper) {
    this.jedisPool = jedisPool;
    this.kayentaObjectMapper = kayentaObjectMapper;
  }

  // Build a list of canary config summaries, including the current persisted index plus entries in the pending updates queue.
  public Set<Map<String, Object>> getCanaryConfigSummarySet(AccountCredentials credentials, List<String> applications) {
    String accountName = credentials.getName();
    String mapByApplicationKey = "kayenta:" + credentials.getType() + ":" + accountName + MAP_BY_APPLICATION_KEY_SUFFIX;
    Set<Map<String, Object>> canaryConfigSummarySet = new HashSet<>();

    try (Jedis jedis = jedisPool.getResource()) {
      if (jedis.exists(mapByApplicationKey)) {
        if (applications != null && applications.size() > 0) {
          // If any applications were specified, populate the response with all of the canary configs scoped to those applications.
          for (String application : applications) {
            String appScopedCanaryConfigListJson = jedis.hget(mapByApplicationKey, application);

            populateCanaryConfigSummarySet(mapByApplicationKey, canaryConfigSummarySet, appScopedCanaryConfigListJson);
          }
        } else {
          // No applications were specified so populate the response with all persisted canary configs.
          List<String> allAppScopedCanaryConfigListJson = jedis.hvals(mapByApplicationKey);

          for (String appScopedCanaryConfigListJson : allAppScopedCanaryConfigListJson) {
            populateCanaryConfigSummarySet(mapByApplicationKey, canaryConfigSummarySet, appScopedCanaryConfigListJson);
          }
        }
      } else {
        throw new IllegalArgumentException("Canary config index not ready.");
      }

      populateWithPendingUpdates(credentials, accountName, jedis, canaryConfigSummarySet, applications);
    }

    return canaryConfigSummarySet;
  }

  // Populate the response with the canary configs scoped to the application, while deduping based on canary config id.
  private void populateCanaryConfigSummarySet(String mapByApplicationKey, Set<Map<String, Object>> canaryConfigSummarySet, String appScopedCanaryConfigSetJson) {
    if (!StringUtils.isEmpty(appScopedCanaryConfigSetJson)) {
      if (appScopedCanaryConfigSetJson.equals(NO_INDEXED_CONFIGS_SENTINEL_VALUE)) {
        return;
      }

      try {
        List<Map<String, Object>> appScopedCanaryConfigList = kayentaObjectMapper.readValue(appScopedCanaryConfigSetJson, new TypeReference<List<Map<String, Object>>>() {});

        for (Map<String, Object> canaryConfigSummary : appScopedCanaryConfigList) {
          String canaryConfigId = (String)canaryConfigSummary.get("id");
          boolean alreadyInList =
            canaryConfigSummarySet
              .stream()
              .filter(it -> it.get("id").equals(canaryConfigId))
              .findFirst()
              .isPresent();

          if (!alreadyInList) {
            canaryConfigSummarySet.add(canaryConfigSummary);
          }
        }
      } catch (IOException e) {
        throw new IllegalArgumentException("Unable to parse index '" + mapByApplicationKey + "': " + e.getMessage(), e);
      }
    }
  }

  // Populate the response with canary config summaries from the pending updates queue.
  private void populateWithPendingUpdates(AccountCredentials credentials, String accountName, Jedis jedis, Set<Map<String, Object>> canaryConfigSummarySet, List<String> applications) {
    String pendingUpdatesKey = buildMapPendingUpdatesByApplicationKey(credentials, accountName);

    if (jedis.exists(pendingUpdatesKey)) {
      List<String> pendingUpdatesJsonList = jedis.lrange(pendingUpdatesKey, 0, -1);

      if (pendingUpdatesJsonList != null && pendingUpdatesJsonList.size() > 0) {
        for (String pendingUpdateCanaryConfigSummaryJson : pendingUpdatesJsonList) {
          try {
            String[] updateTokens = pendingUpdateCanaryConfigSummaryJson.split(":", 5);
            CanaryConfigIndexAction action = CanaryConfigIndexAction.valueOf(updateTokens[1]);
            String startOrFinish = updateTokens[2];

            // In-flight operations are considered already completed as far as the index is concerned.
            if (startOrFinish.equals("start")) {
              pendingUpdateCanaryConfigSummaryJson = updateTokens[4];

              Map<String, Object> pendingUpdateCanaryConfigSummary = kayentaObjectMapper.readValue(pendingUpdateCanaryConfigSummaryJson, new TypeReference<Map<String, Object>>() {});
              String pendingUpdateCanaryConfigId = (String)pendingUpdateCanaryConfigSummary.get("id");
              Map<String, Object> existingCanaryConfigSummary =
                canaryConfigSummarySet
                  .stream()
                  .filter(it -> it.get("id").equals(pendingUpdateCanaryConfigId))
                  .findFirst()
                  .orElse(null);

              // Remove any existing matching summary from the response.
              if (existingCanaryConfigSummary != null) {
                canaryConfigSummarySet.remove(existingCanaryConfigSummary);
              }

              // If the pending update represents an update action, as opposed to a delete, populate the response with the updated summary.
              if (action == CanaryConfigIndexAction.UPDATE) {
                // Populate the response with the canary config summary if either no applications were specified in the request or if the canary config is scoped to at
                // least one of the specified applications.
                if (applications == null || applications.size() == 0 || haveCommonElements(applications, (List<String>)pendingUpdateCanaryConfigSummary.get("applications"))) {
                  canaryConfigSummarySet.add(pendingUpdateCanaryConfigSummary);
                }
              }
            }
          } catch (IOException e) {
            log.error("Problem deserializing pendingUpdateCanaryConfigSummaryJson -> {}: {}", pendingUpdateCanaryConfigSummaryJson, e);
          }
        }
      }
    }
  }

  // Determine if there is at least one common element between these two lists.
  public static boolean haveCommonElements(List<String> listOne, List<String> listTwo) {
    List<String> tempList = new ArrayList<>();
    tempList.addAll(listOne);
    tempList.retainAll(listTwo);
    return tempList.size() > 0;
  }

  public String getIdFromName(AccountCredentials credentials, String canaryConfigName, List<String> applications) {
    Set<Map<String, Object>> canaryConfigSummarySet = getCanaryConfigSummarySet(credentials, applications);

    if (canaryConfigSummarySet != null) {
      Map<String, Object> canaryConfigSummary =
        canaryConfigSummarySet
          .stream()
          .filter(it -> it.get("name").equals(canaryConfigName))
          .findFirst()
          .orElse(null);

      if (canaryConfigSummary != null) {
        return (String)canaryConfigSummary.get("id");
      }
    }

    return null;
  }

  public Map<String, Object> getSummaryFromId(AccountCredentials credentials, String canaryConfigId) {
    Set<Map<String, Object>> canaryConfigSummarySet = getCanaryConfigSummarySet(credentials, null);

    if (canaryConfigSummarySet != null) {
      return canaryConfigSummarySet
        .stream()
        .filter(it -> it.get("id").equals(canaryConfigId))
        .findFirst()
        .orElse(null);
    }

    return null;
  }

  public long getRedisTime() {
    try (Jedis jedis = jedisPool.getResource()) {
      List<String> redisTimeList = jedis.time();

      return Long.parseLong(redisTimeList.get(0)) * 1000 + Long.parseLong(redisTimeList.get(1)) / 1000;
    }
  }

  public void startPendingUpdate(AccountCredentials credentials, String updatedTimestamp, CanaryConfigIndexAction action, String correlationId, String canaryConfigSummaryJson) {
    String accountName = credentials.getName();
    String mapPendingUpdatesByApplicationKey = buildMapPendingUpdatesByApplicationKey(credentials, accountName);

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.rpush(mapPendingUpdatesByApplicationKey, updatedTimestamp + ":" + action + ":start:" + correlationId + ":" + canaryConfigSummaryJson);
    }
  }

  public void finishPendingUpdate(AccountCredentials credentials, CanaryConfigIndexAction action, String correlationId) {
    String accountName = credentials.getName();
    String mapPendingUpdatesByApplicationKey = buildMapPendingUpdatesByApplicationKey(credentials, accountName);

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.rpush(mapPendingUpdatesByApplicationKey, getRedisTime() + ":" + action + ":finish:" + correlationId);
    }
  }

  public void removeFailedPendingUpdate(AccountCredentials credentials, String updatedTimestamp, CanaryConfigIndexAction action, String correlationId, String canaryConfigSummaryJson) {
    String accountName = credentials.getName();
    String mapPendingUpdatesByApplicationKey = buildMapPendingUpdatesByApplicationKey(credentials, accountName);

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.lrem(mapPendingUpdatesByApplicationKey, 1, updatedTimestamp + ":" + action + ":start:" + correlationId + ":" + canaryConfigSummaryJson);
    }
  }

  private String buildMapPendingUpdatesByApplicationKey(AccountCredentials credentials, String accountName) {
    return "kayenta:" + credentials.getType() + ":" + accountName + PENDING_UPDATES_KEY_SUFFIX;
  }
}
