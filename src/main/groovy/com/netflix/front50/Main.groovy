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

package com.netflix.front50

import com.netflix.appinfo.InstanceInfo
import com.netflix.front50.controllers.ApplicationsController
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@EnableAutoConfiguration
@ComponentScan("com.netflix.front50")
public class Main extends SpringBootServletInitializer {

  static void main(_) {
    initializeEnv()
    SpringApplication.run this, [] as String[]
  }

  private static void initializeEnv() {
    if (!System.properties["netflix.environment"]) {
      System.setProperty("netflix.environment", "test")
    }
  }

  @Override
  protected SpringApplicationBuilder configure(final SpringApplicationBuilder application) {
    initializeEnv()
    application.sources(ApplicationsController)
  }

  @Bean
  public InstanceInfo.InstanceStatus instanceStatus() {
    return InstanceInfo.InstanceStatus.UNKNOWN;
  }
}
