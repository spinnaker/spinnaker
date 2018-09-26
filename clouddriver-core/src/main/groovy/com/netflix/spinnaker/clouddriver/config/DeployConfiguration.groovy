/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.config

import com.netflix.spinnaker.clouddriver.data.task.InMemoryTaskRepository
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DefaultDeployHandlerRegistry
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeployHandlerRegistry
import com.netflix.spinnaker.clouddriver.deploy.NullOpDeployHandler
import com.netflix.spinnaker.clouddriver.orchestration.AnnotationsBasedAtomicOperationsRegistry
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry
import com.netflix.spinnaker.clouddriver.orchestration.DefaultOrchestrationProcessor
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DeployConfiguration {
  @Bean
  @ConditionalOnMissingBean(TaskRepository)
  TaskRepository taskRepository() {
    new InMemoryTaskRepository()
  }

  @Bean
  @ConditionalOnMissingBean(DeployHandlerRegistry)
  DeployHandlerRegistry deployHandlerRegistry() {
    new DefaultDeployHandlerRegistry()
  }

  @Bean
  @ConditionalOnMissingBean(OrchestrationProcessor)
  OrchestrationProcessor orchestrationProcessor() {
    new DefaultOrchestrationProcessor()
  }

  @Bean
  @ConditionalOnMissingBean(DeployHandler)
  DeployHandler nullOpDeployHandler() {
    new NullOpDeployHandler()
  }

  @Bean
  AtomicOperationsRegistry atomicOperationsRegistry() {
    new AnnotationsBasedAtomicOperationsRegistry()
  }
}
