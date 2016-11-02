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

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty({"elasticSearch.connection"})
@ComponentScan({"com.netflix.spinnaker.clouddriver.elasticsearch"})
@EnableConfigurationProperties(ElasticSearchConfigProperties.class)
public class ElasticSearchConfig {
  @Bean
  JestClient jestClient(ElasticSearchConfigProperties elasticSearchConfigProperties) {
    String elasticSearchConnection = elasticSearchConfigProperties.getConnection();

    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(
      (new HttpClientConfig.Builder(elasticSearchConnection)).multiThreaded(true).build()
    );
    return factory.getObject();
  }
}
