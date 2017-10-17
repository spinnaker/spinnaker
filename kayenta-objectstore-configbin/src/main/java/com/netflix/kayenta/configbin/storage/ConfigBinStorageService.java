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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.configbin.security.ConfigBinNamedAccountCredentials;
import com.netflix.kayenta.configbin.service.ConfigBinRemoteService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.util.ObjectMapperFactory;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit.RetrofitError;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class ConfigBinStorageService implements StorageService {

  private static final ObjectMapper objectMapper = ObjectMapperFactory.getMapper();

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey) throws IllegalArgumentException {
    ConfigBinNamedAccountCredentials credentials = (ConfigBinNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    String ownerApp = credentials.getOwnerApp();
    String configType = credentials.getConfigType();
    ConfigBinRemoteService remoteService = credentials.getRemoteService();
    String json;
    try {
       json = remoteService.get(ownerApp, configType, objectKey);
    } catch (RetrofitError e) {
      throw new IllegalArgumentException("No such object named " + objectKey);
    }
    try {
      return objectMapper.readValue(json, objectType.getTypeReference());
    } catch (Throwable e) {
      log.error("Read failed on path {}: {}", objectKey, e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public <T> void storeObject(String accountName, ObjectType objectType, String objectKey, T obj) {
    ConfigBinNamedAccountCredentials credentials = (ConfigBinNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    String ownerApp = credentials.getOwnerApp();
    String configType = credentials.getConfigType();
    ConfigBinRemoteService remoteService = credentials.getRemoteService();
    try {
      String json = objectMapper.writeValueAsString(obj);
      RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
      remoteService.post(ownerApp, configType, objectKey, body);
    } catch (IOException e) {
      log.error("Update failed on path {}: {}", objectKey, e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
    ConfigBinNamedAccountCredentials credentials = (ConfigBinNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    String ownerApp = credentials.getOwnerApp();
    String configType = credentials.getConfigType();
    ConfigBinRemoteService remoteService = credentials.getRemoteService();
    remoteService.delete(ownerApp, configType, objectKey);
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(String accountName, ObjectType objectType) {
    ConfigBinNamedAccountCredentials credentials = (ConfigBinNamedAccountCredentials) accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    String ownerApp = credentials.getOwnerApp();
    String configType = credentials.getConfigType();
    ConfigBinRemoteService remoteService = credentials.getRemoteService();
    String jsonBody = remoteService.list(ownerApp, configType);
    try {
      List<String> names = objectMapper.readValue(jsonBody, new TypeReference<List<String>>(){});
      Map<String, Object> objectlist = names
        .stream()
        .collect(Collectors.toMap(Function.identity(), this::metadataFor));
      return Collections.singletonList(objectlist);
    } catch (IOException e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  private ObjectMetadata metadataFor(String key) {
    return ObjectMetadata.builder().name(key).build();
  }
}

@Builder
class ObjectMetadata {

  @NotNull
  public final String name;
}
