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
import java.util.Optional;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.batch.*;
import com.netflix.spinnaker.orca.batch.listeners.SpringBatchExecutionListenerProvider;
import com.netflix.spinnaker.orca.batch.stages.SpringBatchStageBuilderProvider;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static java.util.Collections.emptySet;

@Configuration
@ComponentScan("com.netflix.spinnaker.orca.batch.legacy")
@Import(SpringBatchExecutionListenerProvider.class)
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

  @Bean
  StageBuilderProvider springBatchStageBuilderProvider(ApplicationContext applicationContext,
                                                       Optional<Collection<StageBuilder>> stageBuilders,
                                                       Collection<StageDefinitionBuilder> stageDefinitionBuilders,
                                                       ExecutionListenerProvider executionListenerProvider) {
    return new SpringBatchStageBuilderProvider(applicationContext, stageBuilders.orElse(emptySet()), stageDefinitionBuilders, executionListenerProvider);
  }

}
