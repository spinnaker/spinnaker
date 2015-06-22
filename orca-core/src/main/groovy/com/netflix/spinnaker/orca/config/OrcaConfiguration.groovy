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

import com.netflix.spectator.api.ValueFunction
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStack
import groovy.transform.CompileDynamic

import java.time.Clock
import java.time.Duration
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.eureka.EurekaConfiguration
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.StageTaskPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.persistence.JedisJobRegistry
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.notifications.scheduling.SuspendedPipelinesNotificationHandler
import com.netflix.spinnaker.orca.pipeline.OrchestrationStarter
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.ListableJobLocator
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.SimpleJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.*
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import rx.Scheduler
import rx.schedulers.Schedulers
import static java.time.temporal.ChronoUnit.MINUTES
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Configuration
@Import([EurekaConfiguration])
@ComponentScan(["com.netflix.spinnaker.orca.pipeline", "com.netflix.spinnaker.orca.notifications.scheduling", "com.netflix.spinnaker.orca.initialization"])
@CompileStatic
class OrcaConfiguration {

  @Bean
  Clock clock() {
    Clock.systemDefaultZone()
  }

  @Bean
  Duration minInactivity() {
    Duration.of(3, MINUTES)
  }

  @Bean Scheduler scheduler() {
    Schedulers.io()
  }

  @Bean
  @CompileDynamic
  @ConditionalOnMissingBean(TaskExecutor)
  TaskExecutor getTaskExecutor(ExtendedRegistry extendedRegistry) {
    def executor = new ThreadPoolTaskExecutor(maxPoolSize: 250, corePoolSize: 50)

    def createGuage = { String name ->
      def id = extendedRegistry
        .createId("threadpool.${name}" as String)
        .withTag("id", "TaskExecutor")

      extendedRegistry.gauge(id, executor, new ValueFunction() {
        @Override
        double apply(Object ref) {
          ((ThreadPoolTaskExecutor) ref).threadPoolExecutor."${name}"
        }
      })
    }

    createGuage.call("activeCount")
    createGuage.call("maximumPoolSize")
    createGuage.call("corePoolSize")
    createGuage.call("poolSize")

    return executor
  }

  @Bean @ConditionalOnMissingBean(BatchConfigurer)
  BatchConfigurer batchConfigurer(TaskExecutor taskExecutor) {
    new MultiThreadedBatchConfigurer(taskExecutor)
  }

  @Bean
  JobRegistry jobRegistry(JobExplorer jobExplorer, ExecutionRepository executionRepository, PipelineJobBuilder pipelineJobBuilder) {
    new JedisJobRegistry(jobExplorer, executionRepository, pipelineJobBuilder)
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

  @Bean
  @Scope(SCOPE_PROTOTYPE)
  ObjectMapper mapper() {
    new OrcaObjectMapper()
  }

  @Bean @ConditionalOnMissingBean(name = "orchestrationStore")
  ExecutionStore<Orchestration> orchestrationStore(ObjectMapper mapper) {
    new InMemoryOrchestrationStore(mapper)
  }

  @Bean @ConditionalOnMissingBean(name = "pipelineStore")
  ExecutionStore<Pipeline> pipelineStore(ObjectMapper mapper) {
    new InMemoryPipelineStore(mapper)
  }

  @Bean @ConditionalOnMissingBean(name = "pipelineStack")
  PipelineStack pipelineStack() {
    new InMemoryPipelineStack()
  }

  @Bean
  ExecutionRepository executionRepository(ExecutionStore<Pipeline> pipelineStore,
                                          ExecutionStore<Orchestration> orchestrationStore) {
    new DefaultExecutionRepository(orchestrationStore, pipelineStore)
  }

  @Bean OrchestrationStarter orchestrationStarter() {
    new OrchestrationStarter()
  }

  @Bean @Scope(SCOPE_PROTOTYPE) // Scope is really important here...
  SuspendedPipelinesNotificationHandler suspendedPipelinesNotificationHandler(Map input) {
    new SuspendedPipelinesNotificationHandler(input)
  }

  @Bean @Order(Ordered.LOWEST_PRECEDENCE)
  DefaultExceptionHandler defaultExceptionHandler() {
    new DefaultExceptionHandler()
  }

  @Bean
  TaskTaskletAdapter taskTaskletAdapter(ExecutionRepository executionRepository,
                                        List<ExceptionHandler> exceptionHandlers,
                                        ExtendedRegistry extendedRegistry = new ExtendedRegistry(new NoopRegistry())) {
    new TaskTaskletAdapter(executionRepository, exceptionHandlers, extendedRegistry)
  }

  @Bean
  StageStatusPropagationListener stageStatusPropagationListener(ExecutionRepository executionRepository) {
    new StageStatusPropagationListener(executionRepository)
  }

  @Bean
  StageTaskPropagationListener stageTaskPropagationListener(ExecutionRepository executionRepository) {
    new StageTaskPropagationListener(executionRepository)
  }
}
