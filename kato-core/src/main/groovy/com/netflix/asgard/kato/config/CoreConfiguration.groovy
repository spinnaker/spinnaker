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

package com.netflix.asgard.kato.config

import com.netflix.asgard.kato.data.task.InMemoryTaskRepository
import com.netflix.asgard.kato.data.task.TaskRepository
import com.netflix.asgard.kato.deploy.DefaultDeployHandlerRegistry
import com.netflix.asgard.kato.deploy.DeployHandlerRegistry
import com.netflix.asgard.kato.security.DefaultNamedAccountCredentialsHolder
import com.netflix.asgard.kato.security.NamedAccountCredentials
import com.netflix.asgard.kato.security.NamedAccountCredentialsHolder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoreConfiguration {
  @Bean
  @ConditionalOnMissingBean(TaskRepository)
  TaskRepository taskRepository() {
    new InMemoryTaskRepository()
  }

  @Bean
  @ConditionalOnMissingBean(NamedAccountCredentials)
  NamedAccountCredentialsHolder namedAccountCredentialsHolder() {
    new DefaultNamedAccountCredentialsHolder()
  }

  @Bean
  @ConditionalOnMissingBean(DeployHandlerRegistry)
  DeployHandlerRegistry deployHandlerRegistry() {
    new DefaultDeployHandlerRegistry()
  }
}
