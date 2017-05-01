/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.config;

import java.util.Collection;
import java.util.List;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.batch.*;
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler;
import com.netflix.spinnaker.orca.batch.listeners.SpringBatchExecutionListenerProvider;
import com.netflix.spinnaker.orca.config.OrcaConfiguration;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.ListableJobLocator;
import org.springframework.batch.core.configuration.annotation.BatchConfigurer;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Import({
  SpringBatchExecutionListenerProvider.class,
  SpringBatchActiveExecutionTracker.class
})
public class SpringBatchConfiguration {
  @Bean
  ExecutionRunner springBatchExecutionRunner(Collection<StageDefinitionBuilder> stageDefinitionBuilders,
                                             ExecutionRepository executionRepository,
                                             JobLauncher jobLauncher,
                                             JobRegistry jobRegistry,
                                             JobOperator jobOperator,
                                             JobRepository jobRepository,
                                             JobBuilderFactory jobBuilderFactory,
                                             StepBuilderFactory stepBuilderFactory,
                                             TaskTaskletAdapter taskTaskletAdapter,
                                             Collection<Task> tasks,
                                             ExecutionListenerProvider executionListenerProvider) {
    return new SpringBatchExecutionRunner(
      stageDefinitionBuilders,
      executionRepository,
      jobLauncher,
      jobRegistry,
      jobOperator,
      jobRepository,
      jobBuilderFactory,
      stepBuilderFactory,
      taskTaskletAdapter,
      tasks,
      executionListenerProvider
    );
  }

  @Bean(name = "springBatchTaskExecutor")
  TaskExecutor getTaskExecutor(Registry registry) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setMaxPoolSize(150);
    executor.setCorePoolSize(150);
    OrcaConfiguration.applyThreadPoolMetrics(registry, executor, "TaskExecutor");
    return executor;
  }

  @Bean
  @ConditionalOnMissingBean(BatchConfigurer.class)
  BatchConfigurer batchConfigurer(
    @Qualifier("springBatchTaskExecutor") TaskExecutor taskExecutor
  ) {
    return new MultiThreadedBatchConfigurer(taskExecutor);
  }

  @Bean
  @ConditionalOnMissingBean(JobOperator.class)
  JobOperator jobOperator(JobLauncher jobLauncher, JobRepository jobRepository, JobExplorer jobExplorer,
                          ListableJobLocator jobRegistry) {
    SimpleJobOperator jobOperator = new SimpleJobOperator();
    jobOperator.setJobLauncher(jobLauncher);
    jobOperator.setJobRepository(jobRepository);
    jobOperator.setJobExplorer(jobExplorer);
    jobOperator.setJobRegistry(jobRegistry);
    return jobOperator;
  }

  @Bean
  TaskTaskletAdapter taskTaskletAdapter(ExecutionRepository executionRepository,
                                        List<ExceptionHandler> exceptionHandlers,
                                        StageNavigator stageNavigator,
                                        ContextParameterProcessor contextParameterProcessor,
                                        Registry registry) {
    return new TaskTaskletAdapterImpl(executionRepository, exceptionHandlers, stageNavigator, contextParameterProcessor, registry);
  }
}
