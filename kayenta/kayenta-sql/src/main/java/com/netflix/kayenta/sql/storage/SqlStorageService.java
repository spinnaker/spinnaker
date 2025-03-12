/*
 * Copyright 2023 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.kayenta.sql.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.sql.storage.model.SqlCanaryArchive;
import com.netflix.kayenta.sql.storage.model.SqlCanaryConfig;
import com.netflix.kayenta.sql.storage.model.SqlMetricSetPairs;
import com.netflix.kayenta.sql.storage.model.SqlMetricSets;
import com.netflix.kayenta.sql.storage.repo.SqlCanaryArchiveRepo;
import com.netflix.kayenta.sql.storage.repo.SqlCanaryConfigRepo;
import com.netflix.kayenta.sql.storage.repo.SqlMetricSetPairsRepo;
import com.netflix.kayenta.sql.storage.repo.SqlMetricSetsRepo;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Builder
@Service
@ConditionalOnProperty("kayenta.sql.enabled")
public class SqlStorageService implements StorageService {

  private final ObjectMapper objectMapper;
  private final SqlCanaryArchiveRepo sqlCanaryArchiveRepo;
  private final SqlCanaryConfigRepo sqlCanaryConfigRepo;
  private final SqlMetricSetPairsRepo sqlMetricSetPairsRepo;
  private final SqlMetricSetsRepo sqlMetricSetsRepo;

  @Singular @Getter private List<String> accountNames;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public <T> T loadObject(String accountName, ObjectType objectType, String objectKey)
      throws IllegalArgumentException, NotFoundException {
    if (objectType.equals(ObjectType.CANARY_RESULT_ARCHIVE)) {
      var record =
          sqlCanaryArchiveRepo
              .findById(objectKey)
              .orElseThrow(() -> new NotFoundException("Not found object for id: " + objectKey));

      return mapToObject(record.getContent(), objectType);
    }

    if (objectType.equals(ObjectType.CANARY_CONFIG)) {
      var record =
          sqlCanaryConfigRepo
              .findById(objectKey)
              .orElseThrow(() -> new NotFoundException("Not found object for id: " + objectKey));

      return mapToObject(record.getContent(), objectType);
    }

    if (objectType.equals(ObjectType.METRIC_SET_PAIR_LIST)) {
      var record =
          sqlMetricSetPairsRepo
              .findById(objectKey)
              .orElseThrow(() -> new NotFoundException("Not found object for id: " + objectKey));

      return mapToObject(record.getContent(), objectType);
    }

    if (objectType.equals(ObjectType.METRIC_SET_LIST)) {
      var record =
          sqlMetricSetsRepo
              .findById(objectKey)
              .orElseThrow(() -> new NotFoundException("Not found object for id: " + objectKey));

      return mapToObject(record.getContent(), objectType);
    }

    throw new IllegalArgumentException("Unsupported object type: " + objectType);
  }

  @Override
  public <T> void storeObject(
      String accountName,
      ObjectType objectType,
      String objectKey,
      T obj,
      String filename,
      boolean isAnUpdate) {
    if (objectType.equals(ObjectType.CANARY_RESULT_ARCHIVE)) {
      var draftRecord = new SqlCanaryArchive();
      draftRecord.setId(objectKey);
      draftRecord.setContent(mapToJson(obj, objectType));
      draftRecord.setCreatedAt(Instant.now());
      draftRecord.setUpdatedAt(Instant.now());
      sqlCanaryArchiveRepo.save(draftRecord);
      return;
    }

    if (objectType.equals(ObjectType.CANARY_CONFIG)) {
      var draftRecord = new SqlCanaryConfig();
      draftRecord.setId(objectKey);
      draftRecord.setContent(mapToJson(obj, objectType));
      draftRecord.setCreatedAt(Instant.now());
      draftRecord.setUpdatedAt(Instant.now());
      sqlCanaryConfigRepo.save(draftRecord);
      return;
    }

    if (objectType.equals(ObjectType.METRIC_SET_PAIR_LIST)) {
      var draftRecord = new SqlMetricSetPairs();
      draftRecord.setId(objectKey);
      draftRecord.setContent(mapToJson(obj, objectType));
      draftRecord.setCreatedAt(Instant.now());
      draftRecord.setUpdatedAt(Instant.now());
      sqlMetricSetPairsRepo.save(draftRecord);
      return;
    }

    if (objectType.equals(ObjectType.METRIC_SET_LIST)) {
      var draftRecord = new SqlMetricSets();
      draftRecord.setId(objectKey);
      draftRecord.setContent(mapToJson(obj, objectType));
      draftRecord.setCreatedAt(Instant.now());
      draftRecord.setUpdatedAt(Instant.now());
      sqlMetricSetsRepo.save(draftRecord);
      return;
    }

    throw new IllegalArgumentException("Unsupported object type: " + objectType);
  }

  @Override
  public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
    if (objectType.equals(ObjectType.CANARY_RESULT_ARCHIVE)) {
      sqlCanaryArchiveRepo.deleteById(objectKey);
      return;
    }

    if (objectType.equals(ObjectType.CANARY_CONFIG)) {
      sqlCanaryConfigRepo.deleteById(objectKey);
      return;
    }

    if (objectType.equals(ObjectType.METRIC_SET_PAIR_LIST)) {
      sqlMetricSetPairsRepo.deleteById(objectKey);
      return;
    }

    if (objectType.equals(ObjectType.METRIC_SET_LIST)) {
      sqlMetricSetsRepo.deleteById(objectKey);
      return;
    }

    throw new IllegalArgumentException("Unsupported object type: " + objectType);
  }

  @Override
  public List<Map<String, Object>> listObjectKeys(
      String accountName, ObjectType objectType, List<String> applications, boolean skipIndex) {
    if (objectType.equals(ObjectType.CANARY_RESULT_ARCHIVE)) {
      return StreamSupport.stream(sqlCanaryArchiveRepo.findAll().spliterator(), false)
          .map(r -> mapToObjectMetadata(r.getId(), "canary_archive", r.getUpdatedAt()))
          .collect(Collectors.toList());
    }

    if (objectType.equals(ObjectType.CANARY_CONFIG)) {
      return StreamSupport.stream(sqlCanaryConfigRepo.findAll().spliterator(), false)
          .map(r -> mapToObjectMetadata(r.getId(), "canary_config", r.getUpdatedAt()))
          .collect(Collectors.toList());
    }

    if (objectType.equals(ObjectType.METRIC_SET_PAIR_LIST)) {
      return StreamSupport.stream(sqlMetricSetPairsRepo.findAll().spliterator(), false)
          .map(r -> mapToObjectMetadata(r.getId(), "metric_set_pairs", r.getUpdatedAt()))
          .collect(Collectors.toList());
    }

    if (objectType.equals(ObjectType.METRIC_SET_LIST)) {
      return StreamSupport.stream(sqlMetricSetsRepo.findAll().spliterator(), false)
          .map(r -> mapToObjectMetadata(r.getId(), "metric_sets", r.getUpdatedAt()))
          .collect(Collectors.toList());
    }

    throw new IllegalArgumentException("Unsupported object type: " + objectType);
  }

  private <T> T mapToObject(String json, ObjectType objectType) {
    try {
      return objectMapper.readValue(json, (TypeReference<T>) objectType.getTypeReference());
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to deserialize object for objectType: " + objectType, e);
    }
  }

  private <T> String mapToJson(T obj, ObjectType objectType) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to serialize object for objectType: " + objectType, e);
    }
  }

  public Map<String, Object> mapToObjectMetadata(String id, String name, Instant updatedAt) {
    var objectKeys = new HashMap<String, Object>();
    objectKeys.put("id", id);
    objectKeys.put("name", name);
    objectKeys.put("updatedTimestamp", updatedAt.getEpochSecond());
    objectKeys.put("updatedTimestampIso", updatedAt.toString());
    return objectKeys;
  }
}
