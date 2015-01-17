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

package com.netflix.spinnaker.orca.config

import com.netflix.spinnaker.orca.batch.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.exceptions.NoopExceptionHandler
import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.StageTaskPropagationListener
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.notifications.NoopNotificationHandler
import com.netflix.spinnaker.orca.pipeline.OrchestrationStarter
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.configuration.ListableJobLocator
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.SimpleJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Configuration
@ComponentScan("com.netflix.spinnaker.orca.pipeline")
@CompileStatic
class OrcaConfiguration {

  @Bean @ConditionalOnMissingBean(BatchConfigurer)
  BatchConfigurer batchConfigurer() {
    new MultiThreadedBatchConfigurer()
  }

  @Bean @ConditionalOnMissingBean(JobOperator)
  JobOperator jobOperator(JobLauncher jobLauncher, JobRepository jobRepository, JobExplorer jobExplorer,
                          ListableJobLocator jobRegistry) {
    def jobOperator = new SimpleJobOperator()
    jobOperator.jobLauncher = jobLauncher
    jobOperator.jobRepository = jobRepository
    jobOperator.jobExplorer = jobExplorer
    jobOperator.jobRegistry = jobRegistry
    return jobOperator
  }

  @Bean ObjectMapper mapper() {
    OrcaObjectMapper.DEFAULT
  }

  @Bean @ConditionalOnMissingBean(name = "orchestrationStore")
  ExecutionStore<Orchestration> orchestrationStore(ObjectMapper mapper) {
    new InMemoryOrchestrationStore(mapper)
  }

  @Bean @ConditionalOnMissingBean(name = "pipelineStore")
  ExecutionStore<Pipeline> pipelineStore(ObjectMapper mapper) {
    new InMemoryPipelineStore(mapper)
  }

  @Bean
  ExecutionRepository executionRepository(ExecutionStore<Pipeline> pipelineStore,
                                          ExecutionStore<Orchestration> orchestrationStore) {
    new DefaultExecutionRepository(orchestrationStore, pipelineStore)
  }

  @Bean OrchestrationStarter orchestrationStarter() {
    new OrchestrationStarter()
  }

  @Bean NoopNotificationHandler noopNotificationHandler() {
    new NoopNotificationHandler()
  }

  @Bean @Order(Ordered.LOWEST_PRECEDENCE) DefaultExceptionHandler defaultExceptionHandler() {
    new DefaultExceptionHandler()
  }

  @Bean
  TaskTaskletAdapter taskTaskletAdapter(ExecutionRepository executionRepository,
                                        List<ExceptionHandler> exceptionHandlers) {
    new TaskTaskletAdapter(executionRepository, exceptionHandlers)
  }

  @Bean
  StageStatusPropagationListener stageStatusPropagationListener(ExecutionRepository executionRepository) {
    new StageStatusPropagationListener(executionRepository)
  }

  @Bean StageTaskPropagationListener stageTaskPropagationListener(ExecutionRepository executionRepository) {
    new StageTaskPropagationListener(executionRepository)
  }
}
