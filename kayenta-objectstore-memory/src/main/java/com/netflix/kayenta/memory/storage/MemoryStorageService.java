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
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.springframework.beans.factory.annotation.Autowired;

@Builder
public class MemoryStorageService implements StorageService {
  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired AccountCredentialsRepository accountCredentialsRepository;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  private MemoryNamedAccountCredentials getCredentials(String accountName, ObjectType objectType) {
    MemoryNamedAccountCredentials credentials =
        accountCredentialsRepository.getRequiredOne(accountName);
    credentials.getObjects().putIfAbsent(objectType, new ConcurrentHashMap<>());
    credentials.getMetadata().putIfAbsent(objectType, new ConcurrentHashMap<>());
    return credentials;
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey)
      throws IllegalArgumentException {
    MemoryNamedAccountCredentials credentials = getCredentials(accountName, objectType);
    Object entry = credentials.getObjects().get(objectType).get(objectKey);

    if (entry == null) {
      throw new NotFoundException("No such object named " + objectKey);
    }

    return (T) entry;
  }

  @Override
  public <T> void storeObject(
      String accountName,
      ObjectType objectType,
      String objectKey,
      T obj,
      String filename,
      boolean isAnUpdate) {
    MemoryNamedAccountCredentials credentials = getCredentials(accountName, objectType);

    long currentTimestamp = System.currentTimeMillis();
    Map<String, Object> objectMetadataMap = new HashMap<>();
    objectMetadataMap.put("id", objectKey);
    objectMetadataMap.put("updatedTimestamp", currentTimestamp);
    objectMetadataMap.put("updatedTimestampIso", Instant.ofEpochMilli(currentTimestamp).toString());

    if (objectType == ObjectType.CANARY_CONFIG) {
      CanaryConfig canaryConfig = (CanaryConfig) obj;

      checkForDuplicateCanaryConfig(accountName, objectType, canaryConfig, objectKey);

      objectMetadataMap.put("name", canaryConfig.getName());
      objectMetadataMap.put("applications", canaryConfig.getApplications());
    }

    credentials.getObjects().get(objectType).put(objectKey, obj);
    credentials.getMetadata().get(objectType).put(objectKey, objectMetadataMap);
  }

  private void checkForDuplicateCanaryConfig(
      String accountName, ObjectType objectType, CanaryConfig canaryConfig, String canaryConfigId) {
    String canaryConfigName = canaryConfig.getName();
    List<String> applications = canaryConfig.getApplications();
    List<Map<String, Object>> canaryConfigSummaries =
        listObjectKeys(accountName, objectType, applications, false);
    Map<String, Object> existingCanaryConfigSummary =
        canaryConfigSummaries.stream()
            .filter(it -> it.get("name").equals(canaryConfigName))
            .findFirst()
            .orElse(null);

    // We want to avoid creating a naming collision due to the renaming of an existing canary
    // config.
    if (existingCanaryConfigSummary != null
        && !existingCanaryConfigSummary.get("id").equals(canaryConfigId)) {
      throw new IllegalArgumentException(
          "Canary config with name '"
              + canaryConfigName
              + "' already exists in the scope of applications "
              + applications
              + ".");
    }
  }

  @Override
  public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
    MemoryNamedAccountCredentials credentials = getCredentials(accountName, objectType);

    Object oldValue = credentials.getObjects().get(objectType).remove(objectKey);
    credentials.getMetadata().get(objectType).remove(objectKey);

    if (oldValue == null) {
      throw new IllegalArgumentException("Does not exist");
    }
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(
      String accountName, ObjectType objectType, List<String> applications, boolean skipIndex) {
    MemoryNamedAccountCredentials credentials = getCredentials(accountName, objectType);

    boolean filterOnApplications = applications != null && applications.size() > 0;
    List<Map<String, Object>> result = new ArrayList<>();

    for (Map.Entry<String, Object> entry : credentials.getObjects().get(objectType).entrySet()) {
      String entryKey = entry.getKey();
      if (objectType == ObjectType.CANARY_CONFIG) {
        if (filterOnApplications) {
          CanaryConfig canaryConfig = (CanaryConfig) entry.getValue();

          if (CanaryConfigIndex.haveCommonElements(applications, canaryConfig.getApplications())) {
            result.add(credentials.getMetadata().get(objectType).get(entryKey));
          }
        } else {
          result.add(credentials.getMetadata().get(objectType).get(entryKey));
        }
      } else {
        result.add(credentials.getMetadata().get(objectType).get(entryKey));
      }
    }

    return result;
  }
}
