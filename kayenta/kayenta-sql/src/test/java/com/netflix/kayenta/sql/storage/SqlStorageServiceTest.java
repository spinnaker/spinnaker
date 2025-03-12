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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryExecutionStatusResponse;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.sql.storage.model.SqlCanaryArchive;
import com.netflix.kayenta.sql.storage.model.SqlCanaryConfig;
import com.netflix.kayenta.sql.storage.model.SqlMetricSetPairs;
import com.netflix.kayenta.sql.storage.model.SqlMetricSets;
import com.netflix.kayenta.sql.storage.repo.SqlCanaryArchiveRepo;
import com.netflix.kayenta.sql.storage.repo.SqlCanaryConfigRepo;
import com.netflix.kayenta.sql.storage.repo.SqlMetricSetPairsRepo;
import com.netflix.kayenta.sql.storage.repo.SqlMetricSetsRepo;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class SqlStorageServiceTest {

  private static ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @TestConfiguration
  static class SqlStorageServiceTestConfig {

    @MockBean private SqlCanaryArchiveRepo sqlCanaryArchiveRepo;

    @MockBean private SqlCanaryConfigRepo sqlCanaryConfigRepo;

    @MockBean private SqlMetricSetPairsRepo sqlMetricSetPairsRepo;

    @MockBean private SqlMetricSetsRepo sqlMetricSetsRepo;

    @Bean
    public SqlStorageService sqlStorageService() {
      return new SqlStorageService(
          objectMapper,
          sqlCanaryArchiveRepo,
          sqlCanaryConfigRepo,
          sqlMetricSetPairsRepo,
          sqlMetricSetsRepo,
          new ArrayList<>());
    }
  }

  @Autowired private SqlCanaryArchiveRepo sqlCanaryArchiveRepo;

  @Autowired private SqlCanaryConfigRepo sqlCanaryConfigRepo;

  @Autowired private SqlMetricSetPairsRepo sqlMetricSetPairsRepo;

  @Autowired private SqlMetricSetsRepo sqlMetricSetsRepo;

  @Autowired private SqlStorageService sqlStorageService;

  @Test
  public void testLoadObjectWhenCanaryArchive() throws IOException {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.CANARY_RESULT_ARCHIVE;
    var testObjectKey = UUID.randomUUID().toString();

    var testCanaryExecutionStatusResponse = createTestCanaryExecutionStatusResponse();

    var testSqlCanaryArchive = createTestSqlCanaryArchive(testCanaryExecutionStatusResponse);
    testSqlCanaryArchive.setId(testObjectKey);

    when(sqlCanaryArchiveRepo.findById(testObjectKey))
        .thenReturn(Optional.of(testSqlCanaryArchive));

    var canaryArchive =
        (CanaryExecutionStatusResponse)
            sqlStorageService.loadObject(testAccountName, testObjectType, testObjectKey);

    assertEquals(
        canaryArchive.getApplication(), testCanaryExecutionStatusResponse.getApplication());
    assertEquals(
        canaryArchive.getCanaryConfigId(), testCanaryExecutionStatusResponse.getCanaryConfigId());
    assertEquals(canaryArchive.getPipelineId(), testCanaryExecutionStatusResponse.getPipelineId());
    assertEquals(canaryArchive.getStatus(), testCanaryExecutionStatusResponse.getStatus());
  }

  @Test
  public void testLoadObjectWhenCanaryArchiveNotFound() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.CANARY_RESULT_ARCHIVE;
    var testObjectKey = UUID.randomUUID().toString();

    assertThrows(
        NotFoundException.class,
        () -> sqlStorageService.loadObject(testAccountName, testObjectType, testObjectKey));
  }

  @Test
  public void testLoadObjectWhenCanaryConfig() throws IOException {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.CANARY_CONFIG;
    var testObjectKey = UUID.randomUUID().toString();

    var testCanaryConfig = createTestCanaryConfig();

    var testSqlCanaryConfig = createTestSqlCanaryConfig(testCanaryConfig);
    testSqlCanaryConfig.setId(testObjectKey);

    when(sqlCanaryConfigRepo.findById(testObjectKey)).thenReturn(Optional.of(testSqlCanaryConfig));

    var canaryConfig =
        (CanaryConfig) sqlStorageService.loadObject(testAccountName, testObjectType, testObjectKey);

    assertEquals(canaryConfig.getName(), testCanaryConfig.getName());
    assertEquals(canaryConfig.getApplications(), testCanaryConfig.getApplications());
  }

  @Test
  public void testLoadObjectWhenCanaryConfigWhenNotFound() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.CANARY_CONFIG;
    var testObjectKey = UUID.randomUUID().toString();

    assertThrows(
        NotFoundException.class,
        () -> sqlStorageService.loadObject(testAccountName, testObjectType, testObjectKey));
  }

  @Test
  public void testLoadObjectWhenMetricSetPairs() throws IOException {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.METRIC_SET_PAIR_LIST;
    var testObjectKey = UUID.randomUUID().toString();

    var testMetricSetPair = createTestMetricSetPair();

    var testSqlMetricSetPairs = createTestSqlMetricSetPairs(testMetricSetPair);
    testSqlMetricSetPairs.setId(testObjectKey);

    when(sqlMetricSetPairsRepo.findById(testObjectKey))
        .thenReturn(Optional.of(testSqlMetricSetPairs));

    var metricSetPairs =
        (List<MetricSetPair>)
            sqlStorageService.loadObject(testAccountName, testObjectType, testObjectKey);

    assertNotNull(metricSetPairs);
    assertEquals(metricSetPairs.size(), 1);

    var metricSetPair = metricSetPairs.get(0);

    assertEquals(metricSetPair.getId(), testMetricSetPair.getId());
    assertEquals(metricSetPair.getName(), testMetricSetPair.getName());
    assertEquals(metricSetPair.getTags(), testMetricSetPair.getTags());
    assertEquals(metricSetPair.getValues(), testMetricSetPair.getValues());
    assertEquals(metricSetPair.getAttributes(), testMetricSetPair.getAttributes());
    assertEquals(metricSetPair.getScopes(), testMetricSetPair.getScopes());
  }

  @Test
  public void testLoadObjectWhenMetricSetPairsNotFound() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.METRIC_SET_PAIR_LIST;
    var testObjectKey = UUID.randomUUID().toString();

    assertThrows(
        NotFoundException.class,
        () -> sqlStorageService.loadObject(testAccountName, testObjectType, testObjectKey));
  }

  @Test
  public void testLoadObjectWhenMetricSets() throws IOException {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.METRIC_SET_LIST;
    var testObjectKey = UUID.randomUUID().toString();

    var testMetricSet = createTestMetricSet();

    var testSqlMetricSets = createTestSqlMetricSets(testMetricSet);
    testSqlMetricSets.setId(testObjectKey);

    when(sqlMetricSetsRepo.findById(testObjectKey)).thenReturn(Optional.of(testSqlMetricSets));

    var metricSets =
        (List<MetricSet>)
            sqlStorageService.loadObject(testAccountName, testObjectType, testObjectKey);

    assertNotNull(metricSets);
    assertEquals(metricSets.size(), 1);

    var metricSet = metricSets.get(0);

    assertEquals(metricSet.getName(), testMetricSet.getName());
    assertEquals(metricSet.getTags(), testMetricSet.getTags());
    assertEquals(metricSet.getValues(), testMetricSet.getValues());
    assertEquals(metricSet.getAttributes(), testMetricSet.getAttributes());
  }

  @Test
  public void testLoadObjectWhenMetricSetsNotFound() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.METRIC_SET_LIST;
    var testObjectKey = UUID.randomUUID().toString();

    assertThrows(
        NotFoundException.class,
        () -> sqlStorageService.loadObject(testAccountName, testObjectType, testObjectKey));
  }

  @Test
  public void testStoreObjectWhenCanaryArchive() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.CANARY_RESULT_ARCHIVE;
    var testObjectKey = UUID.randomUUID().toString();

    var testCanaryExecutionStatusResponse = createTestCanaryExecutionStatusResponse();

    sqlStorageService.storeObject(
        testAccountName, testObjectType, testObjectKey, testCanaryExecutionStatusResponse);

    verify(sqlCanaryArchiveRepo).save(any(SqlCanaryArchive.class));
  }

  @Test
  public void testStoreObjectWhenCanaryConfig() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.CANARY_CONFIG;
    var testObjectKey = UUID.randomUUID().toString();

    var testCanaryConfig = createTestCanaryConfig();

    sqlStorageService.storeObject(testAccountName, testObjectType, testObjectKey, testCanaryConfig);

    verify(sqlCanaryConfigRepo).save(any(SqlCanaryConfig.class));
  }

  @Test
  public void testStoreObjectWhenMetricSetPairs() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.METRIC_SET_PAIR_LIST;
    var testObjectKey = UUID.randomUUID().toString();

    var testMetricSetPair = createTestMetricSetPair();

    sqlStorageService.storeObject(
        testAccountName, testObjectType, testObjectKey, List.of(testMetricSetPair));

    verify(sqlMetricSetPairsRepo).save(any(SqlMetricSetPairs.class));
  }

  @Test
  public void testStoreObjectWhenMetricSets() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.METRIC_SET_LIST;
    var testObjectKey = UUID.randomUUID().toString();

    var testMetricSet = createTestMetricSet();

    sqlStorageService.storeObject(
        testAccountName, testObjectType, testObjectKey, List.of(testMetricSet));

    verify(sqlMetricSetsRepo).save(any(SqlMetricSets.class));
  }

  @Test
  public void testDeleteObjectWhenCanaryArchive() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.CANARY_RESULT_ARCHIVE;
    var testObjectKey = UUID.randomUUID().toString();

    sqlStorageService.deleteObject(testAccountName, testObjectType, testObjectKey);

    verify(sqlCanaryArchiveRepo).deleteById(testObjectKey);
  }

  @Test
  public void testDeleteObjectWhenCanaryConfig() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.CANARY_CONFIG;
    var testObjectKey = UUID.randomUUID().toString();

    sqlStorageService.deleteObject(testAccountName, testObjectType, testObjectKey);

    verify(sqlCanaryConfigRepo, times(1)).deleteById(testObjectKey);
  }

  @Test
  public void testDeleteObjectWhenMetricSetPairs() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.METRIC_SET_PAIR_LIST;
    var testObjectKey = UUID.randomUUID().toString();

    sqlStorageService.deleteObject(testAccountName, testObjectType, testObjectKey);

    verify(sqlMetricSetPairsRepo, times(1)).deleteById(testObjectKey);
  }

  @Test
  public void testDeleteObjectWhenMetricSets() {
    var testAccountName = UUID.randomUUID().toString();
    var testObjectType = ObjectType.METRIC_SET_LIST;
    var testObjectKey = UUID.randomUUID().toString();

    sqlStorageService.deleteObject(testAccountName, testObjectType, testObjectKey);

    verify(sqlMetricSetsRepo, times(1)).deleteById(testObjectKey);
  }

  private SqlCanaryArchive createTestSqlCanaryArchive(CanaryExecutionStatusResponse content)
      throws IOException {
    var sqlCanaryArchive = new SqlCanaryArchive();
    sqlCanaryArchive.setId(UUID.randomUUID().toString());
    sqlCanaryArchive.setContent(objectMapper.writeValueAsString(content));
    sqlCanaryArchive.setCreatedAt(Instant.now());
    sqlCanaryArchive.setUpdatedAt(Instant.now());
    return sqlCanaryArchive;
  }

  private SqlCanaryConfig createTestSqlCanaryConfig(CanaryConfig content) throws IOException {
    var sqlCanaryConfig = new SqlCanaryConfig();
    sqlCanaryConfig.setId(UUID.randomUUID().toString());
    sqlCanaryConfig.setContent(objectMapper.writeValueAsString(content));
    sqlCanaryConfig.setCreatedAt(Instant.now());
    sqlCanaryConfig.setUpdatedAt(Instant.now());
    return sqlCanaryConfig;
  }

  private SqlMetricSetPairs createTestSqlMetricSetPairs(MetricSetPair content) throws IOException {
    var sqlMetricSetPairs = new SqlMetricSetPairs();
    sqlMetricSetPairs.setId(UUID.randomUUID().toString());
    sqlMetricSetPairs.setContent(objectMapper.writeValueAsString(List.of(content)));
    sqlMetricSetPairs.setCreatedAt(Instant.now());
    sqlMetricSetPairs.setUpdatedAt(Instant.now());
    return sqlMetricSetPairs;
  }

  private SqlMetricSets createTestSqlMetricSets(MetricSet content) throws IOException {
    var sqlMetricSets = new SqlMetricSets();
    sqlMetricSets.setId(UUID.randomUUID().toString());
    sqlMetricSets.setContent(objectMapper.writeValueAsString(List.of(content)));
    sqlMetricSets.setCreatedAt(Instant.now());
    sqlMetricSets.setUpdatedAt(Instant.now());
    return sqlMetricSets;
  }

  private CanaryExecutionStatusResponse createTestCanaryExecutionStatusResponse() {
    var canaryExecutionStatusResponse = new CanaryExecutionStatusResponse();
    canaryExecutionStatusResponse.setApplication(UUID.randomUUID().toString());
    canaryExecutionStatusResponse.setCanaryConfigId(UUID.randomUUID().toString());
    canaryExecutionStatusResponse.setPipelineId(UUID.randomUUID().toString());
    canaryExecutionStatusResponse.setStatus(UUID.randomUUID().toString());
    return canaryExecutionStatusResponse;
  }

  private CanaryConfig createTestCanaryConfig() {
    var canaryConfig = new CanaryConfig();
    canaryConfig.setName(UUID.randomUUID().toString());
    canaryConfig.setApplications(List.of(UUID.randomUUID().toString()));
    return canaryConfig;
  }

  private MetricSetPair createTestMetricSetPair() {
    return new MetricSetPair(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>());
  }

  private MetricSet createTestMetricSet() {
    var metricSet = new MetricSet();
    metricSet.setName(UUID.randomUUID().toString());
    return metricSet;
  }
}
