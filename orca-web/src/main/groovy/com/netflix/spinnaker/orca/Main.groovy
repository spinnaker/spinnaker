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
import com.netflix.spinnaker.config.TomcatConfiguration
import com.netflix.spinnaker.kork.jedis.JedisConfig
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.config.RedisConfiguration
import com.netflix.spinnaker.orca.data.jackson.StageMixins
import com.netflix.spinnaker.orca.echo.config.EchoConfiguration
import com.netflix.spinnaker.orca.eureka.DiscoveryPollingConfiguration
import com.netflix.spinnaker.orca.flex.config.FlexConfiguration
import com.netflix.spinnaker.orca.front50.config.Front50Configuration
import com.netflix.spinnaker.orca.igor.config.IgorConfiguration
import com.netflix.spinnaker.orca.kato.config.KatoConfiguration
import com.netflix.spinnaker.orca.mayo.config.MayoConfiguration
import com.netflix.spinnaker.orca.mine.config.MineConfiguration
import com.netflix.spinnaker.orca.mort.config.MortConfiguration
import com.netflix.spinnaker.orca.oort.config.OortConfiguration
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.rush.config.RushConfiguration
import com.netflix.spinnaker.orca.tide.config.TideConfiguration
import com.netflix.spinnaker.orca.sock.config.SockConfiguration
import com.netflix.spinnaker.orca.web.config.WebConfiguration
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@EnableAsync
@EnableAutoConfiguration(exclude = [BatchAutoConfiguration, GroovyTemplateAutoConfiguration])
@EnableBatchProcessing(modular = true)
@Import([
  WebConfiguration,
  OrcaConfiguration,
  OrcaPersistenceConfiguration,
  RedisConfiguration,
  JesqueConfiguration,
  BakeryConfiguration,
  EchoConfiguration,
  Front50Configuration,
  FlexConfiguration,
  KatoConfiguration,
  MortConfiguration,
  MayoConfiguration,
  OortConfiguration,
  RushConfiguration,
  IgorConfiguration,
  SockConfiguration,
  DiscoveryPollingConfiguration,
  TomcatConfiguration,
  MineConfiguration,
  TideConfiguration
])
class Main extends SpringBootServletInitializer {
  static final Map<String, String> DEFAULT_PROPS = [
    'netflix.environment': 'test',
    'netflix.account'    : System.getProperty('netflix.environment', 'test'),
    'netflix.stack'      : 'test',
    'spring.config.location': "${System.properties['user.home']}/.spinnaker/",
    'spring.config.name' : 'orca',
    'spring.profiles.active': "${System.getProperty('netflix.environment', 'test')},local"
  ]

  static {
    applyDefaults()
  }

  static void applyDefaults() {
    DEFAULT_PROPS.each { k, v ->
      System.setProperty(k, System.getProperty(k, v))
    }
  }

  static void main(String... args) {
    SpringApplication.run(Main, args)
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application.sources Main
  }

  static class StockMappingJackson2HttpMessageConverter extends MappingJackson2HttpMessageConverter {
  }

  @Bean
  StockMappingJackson2HttpMessageConverter customJacksonConverter(ObjectMapper objectMapper) {
    objectMapper.addMixInAnnotations(PipelineStage, StageMixins)
    new StockMappingJackson2HttpMessageConverter(objectMapper: objectMapper)
  }

}
