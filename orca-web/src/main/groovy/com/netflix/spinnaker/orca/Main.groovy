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

import com.netflix.spinnaker.config.ErrorConfiguration
import com.netflix.spinnaker.config.InterlinkConfiguration
import com.netflix.spinnaker.config.QosConfiguration
import com.netflix.spinnaker.config.StackdriverConfig
import com.netflix.spinnaker.config.TomcatConfiguration
import com.netflix.spinnaker.kork.PlatformComponents
import com.netflix.spinnaker.kork.plugins.spring.SpinnakerApplication
import com.netflix.spinnaker.orca.applications.config.ApplicationConfig
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfiguration
import com.netflix.spinnaker.orca.clouddriver.config.ClouddriverJobConfiguration
import com.netflix.spinnaker.orca.config.CloudFoundryConfiguration
import com.netflix.spinnaker.orca.config.GremlinConfiguration
import com.netflix.spinnaker.orca.config.KeelConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.PipelineTemplateConfiguration
import com.netflix.spinnaker.orca.config.RedisConfiguration
import com.netflix.spinnaker.orca.echo.config.EchoConfiguration
import com.netflix.spinnaker.orca.eureka.DiscoveryPollingConfiguration
import com.netflix.spinnaker.orca.flex.config.FlexConfiguration
import com.netflix.spinnaker.orca.front50.config.Front50Configuration
import com.netflix.spinnaker.orca.igor.config.IgorConfiguration
import com.netflix.spinnaker.orca.kayenta.config.KayentaConfiguration
import com.netflix.spinnaker.orca.mine.config.MineConfiguration
import com.netflix.spinnaker.orca.web.config.WebConfiguration
import com.netflix.spinnaker.orca.webhook.config.WebhookConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@EnableAsync
@EnableAutoConfiguration(exclude = [
  BatchAutoConfiguration,
  GroovyTemplateAutoConfiguration,
  DataSourceAutoConfiguration
])
@Import([
  PlatformComponents,
  WebConfiguration,
  ErrorConfiguration,
  OrcaConfiguration,
  RedisConfiguration,
  BakeryConfiguration,
  EchoConfiguration,
  Front50Configuration,
  FlexConfiguration,
  CloudDriverConfiguration,
  ClouddriverJobConfiguration,
  IgorConfiguration,
  DiscoveryPollingConfiguration,
  TomcatConfiguration,
  MineConfiguration,
  ApplicationConfig,
  StackdriverConfig,
  PipelineTemplateConfiguration,
  KayentaConfiguration,
  WebhookConfiguration,
  KeelConfiguration,
  QosConfiguration,
  CloudFoundryConfiguration,
  GremlinConfiguration,
  InterlinkConfiguration
])
@ComponentScan([
  "com.netflix.spinnaker.config", "com.netflix.spinnaker.plugin"
])
class Main extends SpinnakerApplication {
  static final Map<String, String> DEFAULT_PROPS = [
    'netflix.environment'              : 'test',
    'netflix.account'                  : '${netflix.environment}',
    'netflix.stack'                    : 'test',
    'spring.config.additional-location': '${user.home}/.spinnaker/',
    'spring.application.name'          : 'orca',
    'spring.config.name'               : 'spinnaker,${spring.application.name}',
    'spring.profiles.active'           : '${netflix.environment},local'
  ]

  static void main(String... args) {
    SpinnakerApplication.initialize(DEFAULT_PROPS, Main, args)
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application.properties(DEFAULT_PROPS).sources(Main)
  }
}
