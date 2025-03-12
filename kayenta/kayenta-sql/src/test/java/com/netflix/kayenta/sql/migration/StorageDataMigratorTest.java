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

package com.netflix.kayenta.sql.migration;

import static org.mockito.Mockito.*;

import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.sql.config.DataMigrationProperties;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class StorageDataMigratorTest {

  private static final String testSourceAccountName = "testSourceAccountName";
  private static final String testTargetAccountName = "testTargetAccountName";

  @TestConfiguration
  static class StorageDataMigratorTestConfig {

    @MockBean(name = "sourceStorageService")
    private StorageService sourceStorageService;

    @MockBean(name = "targetStorageService")
    private StorageService targetStorageService;

    @Bean
    public DataMigrationProperties dataMigrationProperties() {
      var dataMigrationProperties = new DataMigrationProperties();
      dataMigrationProperties.setEnabled(true);
      dataMigrationProperties.setSourceAccountName(testSourceAccountName);
      dataMigrationProperties.setTargetAccountName(testTargetAccountName);

      return dataMigrationProperties;
    }

    @Bean
    public StorageDataMigrator storageDataMigrator(
        DataMigrationProperties dataMigrationProperties) {
      return new StorageDataMigrator(
          dataMigrationProperties,
          sourceStorageService,
          targetStorageService,
          MoreExecutors.newDirectExecutorService());
    }
  }

  @Autowired private StorageService sourceStorageService;

  @Autowired private StorageService targetStorageService;

  @Autowired private StorageDataMigrator storageDataMigrator;

  @Test
  public void testMigrateWhenNoCollisions() {
    var testCanaryConfig = createTestCanaryConfig();

    when(sourceStorageService.listObjectKeys(testSourceAccountName, ObjectType.CANARY_CONFIG))
        .thenReturn(List.of(Map.of("id", testCanaryConfig.getId())));

    when(targetStorageService.listObjectKeys(testTargetAccountName, ObjectType.CANARY_CONFIG))
        .thenReturn(new ArrayList<>());

    when(sourceStorageService.loadObject(
            testSourceAccountName, ObjectType.CANARY_CONFIG, testCanaryConfig.getId()))
        .thenReturn(testCanaryConfig);

    storageDataMigrator.migrate();

    verify(sourceStorageService)
        .loadObject(testSourceAccountName, ObjectType.CANARY_CONFIG, testCanaryConfig.getId());

    verify(targetStorageService)
        .storeObject(
            testTargetAccountName,
            ObjectType.CANARY_CONFIG,
            testCanaryConfig.getId(),
            testCanaryConfig);
  }

  @Test
  public void testMigrateWhenCollisions() {
    var testCanaryConfig = createTestCanaryConfig();

    when(sourceStorageService.listObjectKeys(testSourceAccountName, ObjectType.CANARY_CONFIG))
        .thenReturn(List.of(Map.of("id", testCanaryConfig.getId())));

    when(targetStorageService.listObjectKeys(testTargetAccountName, ObjectType.CANARY_CONFIG))
        .thenReturn(List.of(Map.of("id", testCanaryConfig.getId())));

    storageDataMigrator.migrate();

    verify(sourceStorageService, never())
        .loadObject(testSourceAccountName, ObjectType.CANARY_CONFIG, testCanaryConfig.getId());

    verify(targetStorageService, never())
        .storeObject(
            testTargetAccountName,
            ObjectType.CANARY_CONFIG,
            testCanaryConfig.getId(),
            testCanaryConfig);
  }

  private CanaryConfig createTestCanaryConfig() {
    return CanaryConfig.builder()
        .id(UUID.randomUUID().toString())
        .name(UUID.randomUUID().toString())
        .applications(List.of(UUID.randomUUID().toString()))
        .build();
  }
}
