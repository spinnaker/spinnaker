/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.oort.bench

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration
@EnableAutoConfiguration(exclude = GroovyTemplateAutoConfiguration)
@EnableConfigurationProperties
@ComponentScan
class Main {
  static final Map<String, String> DEFAULT_PROPS = [
    'netflix.environment': 'test',
    'netflix.account': System.getProperty('netflix.environment', 'test'),
    'netflix.stack': 'test',
    'spring.config.location': "${System.properties['user.home']}/.spinnaker/",
    'spring.config.name': 'oort-bench',
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

  public static void main(String... args) {
    SpringApplication.run Main, args
  }

  @Bean
  ScheduledExecutorService scheduledExecutorService() {
    Executors.newSingleThreadScheduledExecutor()
  }

  @Bean
  @ConfigurationProperties("endpoints")
  MonitoredEndpoints monitoredEndpoints() {
    new MonitoredEndpoints()
  }
}
