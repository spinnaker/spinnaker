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

package com.netflix.kayenta.configbin.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.configbin.security.ConfigBinNamedAccountCredentials;
import com.netflix.kayenta.configbin.service.ConfigBinRemoteService;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.index.config.CanaryConfigIndexAction;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.util.Retry;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import retrofit.RetrofitError;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class ConfigBinStorageService implements StorageService {

  public final int MAX_RETRIES = 10; // maximum number of times we'll retry a ConfigBin operation
  public final long RETRY_BACKOFF = 1000; // time between retries in millis

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  ObjectMapper kayentaObjectMapper;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  CanaryConfigIndex canaryConfigIndex;

  private final Retry retry = new Retry();

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey) throws IllegalArgumentException, NotFoundException {
    ConfigBinNamedAccountCredentials credentials = (ConfigBinNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    String ownerApp = credentials.getOwnerApp();
    String configType = credentials.getConfigType();
    ConfigBinRemoteService remoteService = credentials.getRemoteService();
    String json;

    try {
      json = retry.retry(() -> remoteService.get(ownerApp, configType, objectKey), MAX_RETRIES, RETRY_BACKOFF);
    } catch (RetrofitError e) {
      throw new NotFoundException("No such object named " + objectKey);
    }

    try {
      return kayentaObjectMapper.readValue(json, objectType.getTypeReference());
    } catch (Throwable e) {
      log.error("Read failed on path {}: {}", objectKey, e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public <T> void storeObject(String accountName, ObjectType objectType, String objectKey, T obj, String filename, boolean isAnUpdate) {
    ConfigBinNamedAccountCredentials credentials = (ConfigBinNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    String ownerApp = credentials.getOwnerApp();
    String configType = credentials.getConfigType();
    ConfigBinRemoteService remoteService = credentials.getRemoteService();

    long updatedTimestamp = -1;
    String correlationId = null;
    String canaryConfigSummaryJson = null;

    if (objectType == ObjectType.CANARY_CONFIG) {
      updatedTimestamp = canaryConfigIndex.getRedisTime();

      CanaryConfig canaryConfig = (CanaryConfig)obj;

      checkForDuplicateCanaryConfig(canaryConfig, objectKey, credentials);

      correlationId = UUID.randomUUID().toString();

      Map<String, Object> canaryConfigSummary = new ImmutableMap.Builder<String, Object>()
        .put("id", objectKey)
        .put("name", canaryConfig.getName())
        .put("updatedTimestamp", updatedTimestamp)
        .put("updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString())
        .put("applications", canaryConfig.getApplications())
        .build();

      try {
        canaryConfigSummaryJson = kayentaObjectMapper.writeValueAsString(canaryConfigSummary);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Problem serializing canaryConfigSummary -> " + canaryConfigSummary, e);
      }

      canaryConfigIndex.startPendingUpdate(
        credentials,
        updatedTimestamp + "",
        CanaryConfigIndexAction.UPDATE,
        correlationId,
        canaryConfigSummaryJson
      );
    }

    try {
      String json = kayentaObjectMapper.writeValueAsString(obj);
      RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
      retry.retry(() -> remoteService.post(ownerApp, configType, objectKey, body), MAX_RETRIES, RETRY_BACKOFF);

      if (objectType == ObjectType.CANARY_CONFIG) {
        canaryConfigIndex.finishPendingUpdate(credentials, CanaryConfigIndexAction.UPDATE, correlationId);
      }
    } catch (Exception e) {
      log.error("Update failed on path {}: {}", objectKey, e);

      if (objectType == ObjectType.CANARY_CONFIG) {
        canaryConfigIndex.removeFailedPendingUpdate(
          credentials,
          updatedTimestamp + "",
          CanaryConfigIndexAction.UPDATE,
          correlationId,
          canaryConfigSummaryJson
        );
      }

      throw new IllegalArgumentException(e);
    }
  }

  private void checkForDuplicateCanaryConfig(CanaryConfig canaryConfig, String canaryConfigId, ConfigBinNamedAccountCredentials credentials) {
    String canaryConfigName = canaryConfig.getName();
    List<String> applications = canaryConfig.getApplications();
    String existingCanaryConfigId = canaryConfigIndex.getIdFromName(credentials, canaryConfigName, applications);

    // We want to avoid creating a naming collision due to the renaming of an existing canary config.
    if (!StringUtils.isEmpty(existingCanaryConfigId) && !existingCanaryConfigId.equals(canaryConfigId)) {
      throw new IllegalArgumentException("Canary config with name '" + canaryConfigName + "' already exists in the scope of applications " + applications + ".");
    }
  }

  @Override
  public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
    ConfigBinNamedAccountCredentials credentials = (ConfigBinNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    String ownerApp = credentials.getOwnerApp();
    String configType = credentials.getConfigType();

    long updatedTimestamp = -1;
    String correlationId = null;
    String canaryConfigSummaryJson = null;

    if (objectType == ObjectType.CANARY_CONFIG) {
      updatedTimestamp = canaryConfigIndex.getRedisTime();

      Map<String, Object> existingCanaryConfigSummary = canaryConfigIndex.getSummaryFromId(credentials, objectKey);

      if (existingCanaryConfigSummary != null) {
        String canaryConfigName = (String)existingCanaryConfigSummary.get("name");
        List<String> applications = (List<String>)existingCanaryConfigSummary.get("applications");

        correlationId = UUID.randomUUID().toString();

        Map<String, Object> canaryConfigSummary = new ImmutableMap.Builder<String, Object>()
          .put("id", objectKey)
          .put("name", canaryConfigName)
          .put("updatedTimestamp", updatedTimestamp)
          .put("updatedTimestampIso", Instant.ofEpochMilli(updatedTimestamp).toString())
          .put("applications", applications)
          .build();

        try {
          canaryConfigSummaryJson = kayentaObjectMapper.writeValueAsString(canaryConfigSummary);
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException("Problem serializing canaryConfigSummary -> " + canaryConfigSummary, e);
        }

        canaryConfigIndex.startPendingUpdate(
          credentials,
          updatedTimestamp + "",
          CanaryConfigIndexAction.DELETE,
          correlationId,
          canaryConfigSummaryJson
        );
      }
    }

    ConfigBinRemoteService remoteService = credentials.getRemoteService();

    // TODO(mgraff): If remoteService.delete() throws an exception when the target config does not exist, we should
    // try/catch it here and then call canaryConfigIndex.removeFailedPendingUpdate() like the other storage service
    // implementations do.
    retry.retry(() -> remoteService.delete(ownerApp, configType, objectKey), MAX_RETRIES, RETRY_BACKOFF);

    if (correlationId != null) {
      canaryConfigIndex.finishPendingUpdate(credentials, CanaryConfigIndexAction.DELETE, correlationId);
    }
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(String accountName, ObjectType objectType, List<String> applications, boolean skipIndex) {
    ConfigBinNamedAccountCredentials credentials = (ConfigBinNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));

    if (!skipIndex && objectType == ObjectType.CANARY_CONFIG) {
      Set<Map<String, Object>> canaryConfigSet = canaryConfigIndex.getCanaryConfigSummarySet(credentials, applications);

      return Lists.newArrayList(canaryConfigSet);
    } else {
      String ownerApp = credentials.getOwnerApp();
      String configType = credentials.getConfigType();
      ConfigBinRemoteService remoteService = credentials.getRemoteService();
      String jsonBody = retry.retry(() -> remoteService.list(ownerApp, configType), MAX_RETRIES, RETRY_BACKOFF);

      try {
        List<String> ids = kayentaObjectMapper.readValue(jsonBody, new TypeReference<List<String>>() {});

        if (ids.size() > 0) {
          return ids
            .stream()
            .map(i -> metadataFor(credentials, i))
            .collect(Collectors.toList());
        }
      } catch (IOException e) {
        log.error("List failed on path {}: {}", ownerApp, e);
      }

      return Collections.emptyList();
    }
  }

  private Map<String, Object> metadataFor(ConfigBinNamedAccountCredentials credentials, String id) {
    // TODO: (mgraff) Should factor out to a common method, or just call .load()
    ConfigBinRemoteService remoteService = credentials.getRemoteService();
    String ownerApp = credentials.getOwnerApp();
    String configType = credentials.getConfigType();
    String json;
    try {
      json = retry.retry(() -> remoteService.get(ownerApp, configType, id), MAX_RETRIES, RETRY_BACKOFF);
    } catch (RetrofitError e) {
      throw new IllegalArgumentException("No such object named " + id);
    }

    CanaryConfig config;
    try {
      config = kayentaObjectMapper.readValue(json, ObjectType.CANARY_CONFIG.getTypeReference());
    } catch (Throwable e) {
      log.error("Read failed on path {}: {}", id, e);
      throw new IllegalStateException(e);
    }
    return new ImmutableMap.Builder<String, Object>()
      .put("id", id)
      .put("name", config.getName())
      .build();
  }
}
