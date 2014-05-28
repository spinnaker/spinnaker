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

package com.netflix.spinnaker.kato

import com.netflix.appinfo.InstanceInfo
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan("com.netflix.spinnaker.kato")
@EnableAutoConfiguration
class Main extends SpringBootServletInitializer {
  static void main(_) {
    imposeInternalFileConfig()
    imposeInternalClasspathConfig()
    SpringApplication.run this, [] as String[]
  }

  static void imposeInternalFileConfig() {
    def internalConfig = new File("${System.properties['user.home']}/.spinnaker/kato-internal.yml")
    if (internalConfig.exists()) {
      System.setProperty("spring.config.location", "${System.properties["spring.config.location"]},${internalConfig.canonicalPath}")
    }
  }

  static void imposeInternalClasspathConfig() {
    def internalConfig = getClass().getResourceAsStream("/kato-internal.yml")
    if (internalConfig) {
      System.setProperty("spring.config.location", "${System.properties["spring.config.location"]},classpath:/kato-internal.yml,classpath*:/kato-internal.yml")
    }
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application.sources Main
  }

  @Bean
  InstanceInfo.InstanceStatus instanceStatus() {
    InstanceInfo.InstanceStatus.UNKNOWN
  }

}
