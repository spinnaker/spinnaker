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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.index.config.IndexConfigurationProperties;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.scheduling.annotation.Scheduled;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.SetParams;

@Slf4j
public class CanaryConfigIndexingAgent extends AbstractHealthIndicator {

  public static final String INDEXING_INSTANCE_KEY = "kayenta:indexing-instance";
  public static final String HEARTBEAT_KEY_PREFIX = "kayenta:heartbeat:";
  public static final String PENDING_UPDATES_KEY_SUFFIX = ":canaryConfig:pending-updates";
  public static final String MAP_BY_APPLICATION_KEY_SUFFIX = ":canaryConfig:by-application";
  public static final String NO_INDEXED_CONFIGS_SENTINEL_VALUE = "[\"no-indexed-canary-configs\"]";

  private final String currentInstanceId;
  private final JedisPool jedisPool;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final ObjectMapper kayentaObjectMapper;
  private final CanaryConfigIndex canaryConfigIndex;
  private final IndexConfigurationProperties indexConfigurationProperties;

  private int cyclesInitiated = 0;
  private int cyclesCompleted = 0;

  public CanaryConfigIndexingAgent(
      String currentInstanceId,
      JedisPool jedisPool,
      AccountCredentialsRepository accountCredentialsRepository,
      StorageServiceRepository storageServiceRepository,
      ObjectMapper kayentaObjectMapper,
      CanaryConfigIndex canaryConfigIndex,
      IndexConfigurationProperties indexConfigurationProperties) {
    this.currentInstanceId = currentInstanceId;
    this.jedisPool = jedisPool;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.kayentaObjectMapper = kayentaObjectMapper;
    this.canaryConfigIndex = canaryConfigIndex;
    this.indexConfigurationProperties = indexConfigurationProperties;
  }

  @Scheduled(fixedDelayString = "#{@indexConfigurationProperties.heartbeatIntervalMS}")
  public void heartbeat() {
    try (Jedis jedis = jedisPool.getResource()) {
      long timestamp = canaryConfigIndex.getRedisTime();

      jedis.setex(HEARTBEAT_KEY_PREFIX + currentInstanceId, 15, timestamp + "");
    }
  }

  @Scheduled(
      initialDelayString = "#{@indexConfigurationProperties.indexingInitialDelayMS}",
      fixedDelayString = "#{@indexConfigurationProperties.indexingIntervalMS}")
  public void indexCanaryConfigs() {
    cyclesInitiated++;

    int indexingLockTTLSec = indexConfigurationProperties.getIndexingLockTTLSec();
    long staleThresholdMS = indexConfigurationProperties.getPendingUpdateStaleEntryThresholdMS();

    try (Jedis jedis = jedisPool.getResource()) {
      long startTime = System.currentTimeMillis();
      String acquiredIndexingLock =
          jedis.set(
              INDEXING_INSTANCE_KEY,
              currentInstanceId,
              SetParams.setParams().nx().ex(indexingLockTTLSec));

      if (!"OK".equals(acquiredIndexingLock)) {
        String lockHolderInstanceId = jedis.get(INDEXING_INSTANCE_KEY);

        if (lockHolderInstanceId != null) {
          // If the instance holding the indexing lock is not showing a heartbeat, we can
          // appropriate the lock.
          if (!jedis.exists(HEARTBEAT_KEY_PREFIX + lockHolderInstanceId)) {
            log.info(
                "Purloining indexing lock from instance "
                    + lockHolderInstanceId
                    + " because that instance has no heartbeat.");

            acquiredIndexingLock =
                jedis.setex(INDEXING_INSTANCE_KEY, indexingLockTTLSec, currentInstanceId);
          }
        }
      }

      if ("OK".equals(acquiredIndexingLock)) {
        Set<AccountCredentials> accountCredentialsSet =
            CredentialsHelper.getAllAccountsOfType(
                AccountCredentials.Type.CONFIGURATION_STORE, accountCredentialsRepository);

        for (AccountCredentials credentials : accountCredentialsSet) {
          String accountName = credentials.getName();

          try {
            String pendingUpdatesKey =
                "kayenta:" + credentials.getType() + ":" + accountName + PENDING_UPDATES_KEY_SUFFIX;

            // We atomically capture all of the current entries in the pending updates queue here to
            // avoid flushing entries
            // that appeared after we already began scanning the storage system (since that could
            // allow us to flush an
            // un-indexed change). This approach also allows for open start entries to disappear
            // from the pending updates
            // queue while a re-indexing is underway (this can happen if a storage service operation
            // fails prior to closing
            // an open start entry by recording the matching finish entry).
            List<String> updatesThroughCheckpoint = jedis.lrange(pendingUpdatesKey, 0, -1);
            StorageService configurationService =
                storageServiceRepository
                    .getOne(accountName)
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "No storage service was configured; unable to index configurations."));

            List<Map<String, Object>> canaryConfigObjectKeys =
                configurationService.listObjectKeys(
                    accountName, ObjectType.CANARY_CONFIG, null, true);
            Map<String, List<Map>> applicationToCanaryConfigListMap = new HashMap<>();

            for (Map<String, Object> canaryConfigSummary : canaryConfigObjectKeys) {
              try {
                String canaryConfigId = (String) canaryConfigSummary.get("id");
                String canaryConfigName = (String) canaryConfigSummary.get("name");
                Long updatedTimestamp = (Long) canaryConfigSummary.get("updatedTimestamp");
                String updatedTimestampIso =
                    (String) canaryConfigSummary.get("updatedTimestampIso");

                if (updatedTimestamp == null) {
                  updatedTimestamp = canaryConfigIndex.getRedisTime();
                  updatedTimestampIso = Instant.ofEpochMilli(updatedTimestamp).toString();
                }

                CanaryConfig canaryConfig =
                    configurationService.loadObject(
                        accountName, ObjectType.CANARY_CONFIG, canaryConfigId);
                List<String> applications = canaryConfig.getApplications();

                for (String application : applications) {
                  List<Map> canaryConfigList = applicationToCanaryConfigListMap.get(application);

                  if (canaryConfigList == null) {
                    canaryConfigList = new ArrayList<Map>();
                    applicationToCanaryConfigListMap.put(application, canaryConfigList);
                  }

                  canaryConfigList.add(
                      new ImmutableMap.Builder<String, Object>()
                          .put("id", canaryConfigId)
                          .put("name", canaryConfigName)
                          .put("updatedTimestamp", updatedTimestamp)
                          .put("updatedTimestampIso", updatedTimestampIso)
                          .put("applications", applications)
                          .build());
                }
              } catch (NotFoundException e) {
                // This can happen if a re-indexing is underway and we attempt to retrieve a canary
                // config that has been
                // deleted. Don't need to take any action.
              }
            }

            Map<String, String> applicationToSerializedCanaryConfigListMap =
                new HashMap<String, String>();

            for (Map.Entry<String, List<Map>> entry : applicationToCanaryConfigListMap.entrySet()) {
              try {
                applicationToSerializedCanaryConfigListMap.put(
                    entry.getKey(), kayentaObjectMapper.writeValueAsString(entry.getValue()));
              } catch (JsonProcessingException e) {
                log.error(
                    "Problem serializing applicationToCanaryConfigListMap entry -> {}: {}",
                    entry.getValue(),
                    e);
              }
            }

            String mapByApplicationKey =
                "kayenta:"
                    + credentials.getType()
                    + ":"
                    + accountName
                    + MAP_BY_APPLICATION_KEY_SUFFIX;
            Set<String> oldMapByApplicationKeys = jedis.hkeys(mapByApplicationKey);
            Set<String> byApplicationKeysToDelete = new HashSet<>();
            // Application keys to delete should be all the original applications minus all the
            // currently-observed applications.
            byApplicationKeysToDelete.addAll(oldMapByApplicationKeys);
            byApplicationKeysToDelete.removeAll(
                applicationToSerializedCanaryConfigListMap.keySet());

            if (applicationToSerializedCanaryConfigListMap.size() > 0) {
              jedis.hmset(mapByApplicationKey, applicationToSerializedCanaryConfigListMap);
            }

            if (byApplicationKeysToDelete.size() > 0) {
              jedis.hdel(
                  mapByApplicationKey,
                  byApplicationKeysToDelete.toArray(new String[byApplicationKeysToDelete.size()]));
            }

            // We do this so we can distinguish between a completely empty index and an
            // unavailable/missing index.
            if (!jedis.exists(mapByApplicationKey)) {
              jedis.hset(
                  mapByApplicationKey,
                  "not-a-real-application:" + currentInstanceId,
                  NO_INDEXED_CONFIGS_SENTINEL_VALUE);
            }

            // Now that we've scanned all of the canary configs in the storage system and updated
            // the index, we can flush
            // the pending updates queue entries subsumed by the up-to-date index.
            if (updatesThroughCheckpoint.size() > 0) {
              long currentTimestamp = canaryConfigIndex.getRedisTime();
              Map<String, String> encounteredUpdateStarts = new HashMap<>();
              List<String> updatesToFlush = new ArrayList<>();

              for (String updateDescriptor : updatesThroughCheckpoint) {
                String[] updateTokens = updateDescriptor.split(":", 5);
                long updateTimestamp = Long.parseLong(updateTokens[0]);
                long ageMS = currentTimestamp - updateTimestamp;

                // This can happen if we lose a kayenta instance while an operation is in-flight.
                if (ageMS > staleThresholdMS) {
                  updatesToFlush.add(updateDescriptor);
                } else {
                  String startOrFinish = updateTokens[2];
                  String updateId = updateTokens[3];

                  if (startOrFinish.equals("finish")
                      && encounteredUpdateStarts.containsKey("start:" + updateId)) {
                    // Mark matching start/finish entries for removal.
                    updatesToFlush.add(encounteredUpdateStarts.get("start:" + updateId));
                    updatesToFlush.add(updateDescriptor);
                    encounteredUpdateStarts.remove("start:" + updateId);
                  } else if (startOrFinish.equals("start")) {
                    // Note that we've seen this start entry so that we can potentially match it up
                    // with its finish entry.
                    encounteredUpdateStarts.put("start:" + updateId, updateDescriptor);
                  }
                }
              }

              for (String updateToFlush : updatesToFlush) {
                jedis.lrem(pendingUpdatesKey, 1, updateToFlush);
              }
            }
          } catch (Exception e) {
            log.error("Problem indexing account {}: ", accountName, e);
          }
        }

        jedis.del(INDEXING_INSTANCE_KEY);

        long endTime = System.currentTimeMillis();
        Duration duration =
            Duration.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime));

        log.info("Re-indexed canary configs in " + duration + ".");
      } else {
        log.debug("Failed to acquire indexing lock.");
      }

      cyclesCompleted++;
    } catch (JedisException e) {
      log.error("Jedis issue in canary config indexing agent: ", e);
    }
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    Set<AccountCredentials> configurationStoreAccountCredentialsSet =
        CredentialsHelper.getAllAccountsOfType(
            AccountCredentials.Type.CONFIGURATION_STORE, accountCredentialsRepository);
    int existingByApplicationIndexCount = 0;

    try (Jedis jedis = jedisPool.getResource()) {
      for (AccountCredentials credentials : configurationStoreAccountCredentialsSet) {
        String accountName = credentials.getName();
        String mapByApplicationKey =
            "kayenta:" + credentials.getType() + ":" + accountName + MAP_BY_APPLICATION_KEY_SUFFIX;

        if (jedis.exists(mapByApplicationKey)) {
          existingByApplicationIndexCount++;
        }
      }
    }

    int expectedByApplicationIndexCount = configurationStoreAccountCredentialsSet.size();
    // So long as this instance has performed an indexing, or failed to acquire the lock since
    // another instance was in
    // the process of indexing, the index should be available. We also verify that the number of
    // by-application index
    // keys matches the number of configured configuration store accounts.
    if (cyclesCompleted > 0 && existingByApplicationIndexCount == expectedByApplicationIndexCount) {
      builder.up();
    } else {
      builder.down();
    }

    builder.withDetail("existingByApplicationIndexCount", existingByApplicationIndexCount);
    builder.withDetail("expectedByApplicationIndexCount", expectedByApplicationIndexCount);
    builder.withDetail("cyclesInitiated", cyclesInitiated);
    builder.withDetail("cyclesCompleted", cyclesCompleted);
  }
}
