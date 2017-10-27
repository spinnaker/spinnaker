/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.memory.storage;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.memory.security.MemoryNamedAccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Builder
@Slf4j
public class MemoryStorageService implements StorageService {

  public static class MemoryStorageServiceBuilder {
    private Map<String, Object> entries = new ConcurrentHashMap<>();
    private Map<String, Map<String, Object>> entryMetadata = new ConcurrentHashMap<>();
  }

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  private Map<String, Object> entries;
  private Map<String, Map<String, Object>> entryMetadata;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey) throws IllegalArgumentException {
    String key = makeKey(accountName, objectType, objectKey);
    log.info("Getting object type {}, key {}", objectType.toString(), key);
    Object entry = entries.get(key);

    if (entry == null) {
      throw new IllegalArgumentException("No such object named " + key);
    }

    return (T)entry;
  }

  @Override
  public <T> void storeObject(String accountName, ObjectType objectType, String objectKey, T obj, String filename, boolean isAnUpdate) {
    String key = makeKey(accountName, objectType, objectKey);
    log.info("Writing key {}", key);

    long currentTimestamp = System.currentTimeMillis();
    Map<String, Object> objectMetadataMap = new HashMap<>();
    objectMetadataMap.put("id", objectKey);
    objectMetadataMap.put("updatedTimestamp", currentTimestamp);
    objectMetadataMap.put("updatedTimestampIso", Instant.ofEpochMilli(currentTimestamp).toString());

    if (objectType == ObjectType.CANARY_CONFIG) {
      CanaryConfig canaryConfig = (CanaryConfig)obj;

      checkForDuplicateCanaryConfig(accountName, objectType, canaryConfig, objectKey);

      objectMetadataMap.put("name", canaryConfig.getName());
      objectMetadataMap.put("applications", canaryConfig.getApplications());
    }

    entries.put(key, obj);
    entryMetadata.put(key, objectMetadataMap);
  }

  private void checkForDuplicateCanaryConfig(String accountName, ObjectType objectType, CanaryConfig canaryConfig, String canaryConfigId) {
    String canaryConfigName = canaryConfig.getName();
    List<String> applications = canaryConfig.getApplications();
    List<Map<String, Object>> canaryConfigSummaries = listObjectKeys(accountName, objectType, applications, false);
    Map<String, Object> existingCanaryConfigSummary = canaryConfigSummaries
      .stream()
      .filter(it -> it.get("name").equals(canaryConfigName))
      .findFirst()
      .orElse(null);

    // We want to avoid creating a naming collision due to the renaming of an existing canary config.
    if (existingCanaryConfigSummary != null && !existingCanaryConfigSummary.get("id").equals(canaryConfigId)) {
      throw new IllegalArgumentException("Canary config with name '" + canaryConfigName + "' already exists in the scope of applications " + applications + ".");
    }
  }

  @Override
  public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
    String key = makeKey(accountName, objectType, objectKey);
    log.info("Deleting key {}", key);
    Object oldValue = entries.remove(key);
    entryMetadata.remove(key);

    if (oldValue == null) {
      log.error("Object named {} does not exist", key);
      throw new IllegalArgumentException("Does not exist");
    }
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(String accountName, ObjectType objectType, List<String> applications, boolean skipIndex) {
    boolean filterOnApplications = applications != null && applications.size() > 0;
    List<Map<String, Object>> result = new ArrayList<>();

    for (Map.Entry<String, Object> entry : entries.entrySet()) {
      if (objectType == ObjectType.CANARY_CONFIG) {
        if (filterOnApplications) {
          CanaryConfig canaryConfig = (CanaryConfig)entry.getValue();

          if (CanaryConfigIndex.haveCommonElements(applications, canaryConfig.getApplications())) {
            result.add(entryMetadata.get(entry.getKey()));
          }
        } else {
          result.add(entryMetadata.get(entry.getKey()));
        }
      } else {
        result.add(entryMetadata.get(entry.getKey()));
      }
    }

    return result;
  }

  private String makePrefix(String accountName, ObjectType objectType) {
    MemoryNamedAccountCredentials credentials = (MemoryNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    String namespace = credentials.getNamespace();
    String typename = objectType.toString();
    return namespace + ":" + typename + ":";
  }

  private String makeKey(String accountName, ObjectType objectType, String objectKey) {
    return makePrefix(accountName, objectType) + objectKey;
  }
}
