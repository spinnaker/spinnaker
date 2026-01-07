/*
 * Copyright 2025 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.entities;

import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationStorageFilter;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class EntityStoreConfiguration {
  @Bean(name = "serializerEntityHandlers")
  public ArtifactHandlerLists serializerEntityHandlers(
      Map<String, List<ApplicationStorageFilter>> excludeFilters) {

    return ArtifactHandlerLists.builder()
        .mapHandlers(List.of(new ManifestMapStorageHandler(excludeFilters)))
        .collectionHandlers(List.of(new ManifestStorageCollectionHandler(excludeFilters)))
        .build();
  }

  @Bean(name = "deserializerEntityHandlers")
  public ArtifactHandlerLists deserializerEntityHandlers() {
    return ArtifactHandlerLists.builder().mapHandlers(List.of(new ExpandToMapHandler())).build();
  }
}
