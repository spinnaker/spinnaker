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

package com.netflix.kato

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.appinfo.InstanceInfo
import com.netflix.kato.data.task.InMemoryTaskRepository
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.security.DefaultNamedAccountCredentialsHolder
import com.netflix.kato.security.NamedAccountCredentialsHolder
import com.netflix.kato.security.aws.AmazonNamedAccountCredentials
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

@Log4j
@Configuration
@ComponentScan("com.netflix.kato")
@EnableAutoConfiguration
class Main extends SpringBootServletInitializer {

  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  @Autowired
  AWSCredentialsProvider awsCredentialsProvider

  @PostConstruct
  void addCredentials() {
    namedAccountCredentialsHolder.put "test", new AmazonNamedAccountCredentials(awsCredentialsProvider, "test")
  }

  static void main(_) {
    SpringApplication.run this, [] as String[]
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application.sources Main
  }

  @Bean
  TaskRepository taskRepository() {
    new InMemoryTaskRepository()
  }

  @Bean
  NamedAccountCredentialsHolder namedAccountCredentialsHolder() {
    new DefaultNamedAccountCredentialsHolder()
  }

  @Bean
  InstanceInfo.InstanceStatus instanceStatus() {
    InstanceInfo.InstanceStatus.UNKNOWN
  }
}
