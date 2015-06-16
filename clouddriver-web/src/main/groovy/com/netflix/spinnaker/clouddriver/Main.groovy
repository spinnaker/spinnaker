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

package com.netflix.spinnaker.clouddriver

import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration
import com.netflix.spinnaker.clouddriver.controllers.CacheController
import com.netflix.spinnaker.clouddriver.controllers.CredentialsController
import com.netflix.spinnaker.clouddriver.core.CloudDriverConfig
import com.netflix.spinnaker.clouddriver.core.RedisConfig
import com.netflix.spinnaker.clouddriver.filters.SimpleCORSFilter
import org.springframework.boot.SpringApplication
import org.springframework.boot.actuate.autoconfigure.ManagementSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import sun.net.InetAddressCachePolicy

import java.security.Security

@Configuration
@Import([
  WebConfig,
  CloudDriverConfig,
  RedisConfig,
  AwsConfiguration,
  com.netflix.spinnaker.kato.Main,
  com.netflix.spinnaker.mort.Main,
  com.netflix.spinnaker.oort.Main,
])
@EnableAutoConfiguration(exclude = [BatchAutoConfiguration, GroovyTemplateAutoConfiguration, SecurityAutoConfiguration, ManagementSecurityAutoConfiguration])
class Main extends SpringBootServletInitializer {

  static final Map<String, String> DEFAULT_PROPS = [
    'netflix.environment'   : 'test',
    'netflix.account'       : System.getProperty('netflix.environment', 'test'),
    'netflix.stack'         : 'test',
    'spring.config.location': "${System.properties['user.home']}/.spinnaker/",
    'spring.config.name'    : 'clouddriver',
    'spring.profiles.active': "${System.getProperty('netflix.environment', 'test')},local"
  ]

  static {
    applyDefaults()

    /**
     * We often operate in an environment where we expect resolution of DNS names for remote dependencies to change
     * frequently, so it's best to tell the JVM to avoid caching DNS results internally.
     */
    InetAddressCachePolicy.cachePolicy = InetAddressCachePolicy.NEVER
    Security.setProperty('networkaddress.cache.ttl', '0')
  }

  static void applyDefaults() {
    DEFAULT_PROPS.each { k, v ->
      System.setProperty(k, System.getProperty(k, v))
    }
  }

  static void main(String... args) {
    SpringApplication.run this, args
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application.sources Main
  }


}

