/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.jedis.JedisConfig
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.batch.config.JedisConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.data.jackson.StageMixins
import com.netflix.spinnaker.orca.echo.config.EchoConfiguration
import com.netflix.spinnaker.orca.front50.config.Front50Configuration
import com.netflix.spinnaker.orca.kato.config.KatoConfiguration
import com.netflix.spinnaker.orca.mayo.config.MayoConfiguration
import com.netflix.spinnaker.orca.mort.config.MortConfiguration
import com.netflix.spinnaker.orca.oort.config.OortConfiguration
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.web.config.WebConfiguration
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@EnableAsync
@EnableAutoConfiguration(exclude = BatchAutoConfiguration)
@EnableBatchProcessing(modular = true)
@Import([
  OrcaConfiguration,
  JedisConfig,
  JedisConfiguration,
  BakeryConfiguration,
  EchoConfiguration,
  Front50Configuration,
  KatoConfiguration,
  MortConfiguration,
  OortConfiguration,
  WebConfiguration,
  MayoConfiguration
])
class Main {

  static void main(String... args) {
    System.setProperty('netflix.environment', 'test')
    SpringApplication.run(Main, args)
  }

  static class StockMappingJackson2HttpMessageConverter extends MappingJackson2HttpMessageConverter {
  }

  @Bean
  StockMappingJackson2HttpMessageConverter customJacksonConverter(ObjectMapper objectMapper) {
    objectMapper.addMixInAnnotations(PipelineStage, StageMixins)
    new StockMappingJackson2HttpMessageConverter(objectMapper: objectMapper)
  }
}
