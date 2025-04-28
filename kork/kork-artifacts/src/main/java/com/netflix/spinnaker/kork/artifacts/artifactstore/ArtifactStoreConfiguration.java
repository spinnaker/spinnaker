/*
 * Copyright 2023 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationStorageFilter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.RegexApplicationStorageFilter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.s3.S3ArtifactStoreConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan
@EnableConfigurationProperties(ArtifactStoreConfigurationProperties.class)
@Import(S3ArtifactStoreConfiguration.class)
public class ArtifactStoreConfiguration {
  /**
   * this is strictly used due to Spring and Jackson not behaving nicely together.
   * Unfortunately, @JsonDeserializer will construct its own deserializer utilizing beans and thus
   * not using the object mapper we want to use
   */
  @Bean
  public ObjectMapper artifactObjectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public ArtifactStoreURIBuilder artifactStoreURIBuilder() {
    return new ArtifactStoreURISHA256Builder();
  }

  @ConditionalOnMissingBean(ArtifactStoreGetter.class)
  @Bean
  public ArtifactStoreGetter artifactStoreGetter() {
    return new NoopArtifactStoreGetter();
  }

  @ConditionalOnMissingBean(ArtifactStoreStorer.class)
  @Bean
  public ArtifactStoreStorer artifactStoreStorer() {
    return new NoopArtifactStoreStorer();
  }

  @Bean
  public ArtifactStore artifactStore(
      ArtifactStoreGetter artifactStoreGetter,
      ArtifactStoreStorer artifactStoreStorer,
      Map<String, List<ApplicationStorageFilter>> exclude) {
    ArtifactStore artifactStore =
        new ArtifactStore(artifactStoreGetter, artifactStoreStorer, exclude);
    ArtifactStore.setInstance(artifactStore);
    return artifactStore;
  }

  @Bean
  public Map<String, List<ApplicationStorageFilter>> excludeFilters(
      ArtifactStoreConfigurationProperties properties) {
    List<ArtifactStoreConfigurationProperties.ArtifactFilter> artifactFilters =
        properties.getExclude();
    Map<String, List<ApplicationStorageFilter>> exclude = new HashMap<>();
    for (ArtifactStoreConfigurationProperties.ArtifactFilter filter : artifactFilters) {
      if (!exclude.containsKey(filter.type)) {
        exclude.put(filter.type, new ArrayList<>());
      }
      exclude.get(filter.type).add(new RegexApplicationStorageFilter(filter.value));
    }
    return exclude;
  }
}
