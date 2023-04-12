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

package com.netflix.kayenta.sql.config;

import com.netflix.kayenta.sql.migration.StorageDataMigrator;
import com.netflix.kayenta.storage.StorageService;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("kayenta.data-migration.enabled")
@EnableConfigurationProperties(DataMigrationProperties.class)
@RequiredArgsConstructor
public class DataMigrationConfiguration {

  private final DataMigrationProperties dataMigrationProperties;
  private final List<StorageService> storageServices;

  @Bean
  public StorageDataMigrator storageDataMigrator() throws IOException {
    var sourceStorageServiceClassName = dataMigrationProperties.getSourceStorageServiceClassName();
    var targetStorageServiceClassName = dataMigrationProperties.getTargetStorageServiceClassName();

    var sourceStorageService = findStorageService(sourceStorageServiceClassName);
    var targetStorageService = findStorageService(targetStorageServiceClassName);

    return new StorageDataMigrator(
        dataMigrationProperties,
        sourceStorageService,
        targetStorageService,
        Executors.newCachedThreadPool());
  }

  private StorageService findStorageService(String className) throws IOException {
    return storageServices.stream()
        .filter(storageService -> storageService.getClass().getCanonicalName().equals(className))
        .findFirst()
        .orElseThrow(
            () ->
                new IOException(
                    String.format("Failed to find storage service for class name: %s", className)));
  }
}
