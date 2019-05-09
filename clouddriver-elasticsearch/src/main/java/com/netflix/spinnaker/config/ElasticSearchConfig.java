/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchEntityTagger;
import com.netflix.spinnaker.clouddriver.elasticsearch.converters.DeleteEntityTagsAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.elasticsearch.converters.UpsertEntityTagsAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty({"elastic-search.connection"})
@ComponentScan({"com.netflix.spinnaker.clouddriver.elasticsearch"})
@EnableConfigurationProperties(ElasticSearchConfigProperties.class)
public class ElasticSearchConfig {
  @Bean
  JestClient jestClient(ElasticSearchConfigProperties elasticSearchConfigProperties) {
    String elasticSearchConnection = elasticSearchConfigProperties.getConnection();

    JestClientFactory factory = new JestClientFactory();

    HttpClientConfig.Builder builder = new HttpClientConfig.Builder(elasticSearchConnection)
      .readTimeout(elasticSearchConfigProperties.getReadTimeout())
      .connTimeout(elasticSearchConfigProperties.getConnectionTimeout())
      .multiThreaded(true);

    factory.setHttpClientConfig(builder.build());
    return factory.getObject();
  }

  @Bean
  ElasticSearchEntityTagger elasticSearchEntityTagger(ElasticSearchEntityTagsProvider elasticSearchEntityTagsProvider,
                                                      UpsertEntityTagsAtomicOperationConverter upsertEntityTagsAtomicOperationConverter,
                                                      DeleteEntityTagsAtomicOperationConverter deleteEntityTagsAtomicOperationConverter) {
    return new ElasticSearchEntityTagger(
      elasticSearchEntityTagsProvider, upsertEntityTagsAtomicOperationConverter, deleteEntityTagsAtomicOperationConverter
    );
  }
}
