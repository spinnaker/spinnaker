/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.SpringBatchExecutionRunner
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapterImpl
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.listeners.SpringBatchExecutionListenerProvider
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.NoStrategy
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.Strategy
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CreateServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage.CompleteParallelDeployTask
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DetermineSourceServerGroupTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionStage
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionTask
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import groovy.transform.CompileStatic
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.support.GenericApplicationContext
import org.springframework.retry.backoff.Sleeper
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

/**
 * This tests some orca-clouddriver specific stuff around strategies and making
 * sure their task graphs execute correctly.
 *
 * @see ExecutionRunnerSpec
 */
@ContextConfiguration(classes = [
  StageNavigator, WaitForRequisiteCompletionTask, Config,
  WaitForRequisiteCompletionStage, ParallelDeployStage, CreateServerGroupStage,
  TestStrategy, NoStrategy, TestStage, NoOpTask
])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
abstract class DeploymentStrategiesExecutionSpec<R extends ExecutionRunner> extends Specification {

  private static
  final TaskResult SUCCESS = TaskResult.SUCCEEDED

  abstract R create(StageDefinitionBuilder... stageDefBuilders)

  @Autowired GenericApplicationContext applicationContext

  @Autowired ExecutionRepository executionRepository

  @Autowired DetermineSourceServerGroupTask determineSourceServerGroupTask
  @Autowired DetermineHealthProvidersTask determineHealthProvidersTask
  @Autowired CreateServerGroupTask createServerGroupTask
  @Autowired MonitorKatoTask monitorKatoTask
  @Autowired ServerGroupCacheForceRefreshTask serverGroupCacheForceRefreshTask
  @Autowired WaitForUpInstancesTask waitForUpInstancesTask
  @Autowired CompleteParallelDeployTask completeParallelDeployTask
  @Autowired ExecutionRunnerSpec.TestTask testTask

  @Unroll
  def "parallel deploy stages create server groups"() {
    given:
    def stage = new Stage<>(execution, "deploy", "deploy", deployStageContext("prod", "none", *regions))
    execution.stages << stage

    and:
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    @Subject runner = create()

    when:
    runner.start(execution)

    then:
    branches * determineSourceServerGroupTask.execute(_) >> SUCCESS
    branches * determineHealthProvidersTask.execute(_) >> SUCCESS

    and:
    branches * createServerGroupTask.execute(_) >> SUCCESS
    branches * monitorKatoTask.execute(_) >> SUCCESS
    (2 * branches) * serverGroupCacheForceRefreshTask.execute(_) >> SUCCESS
    branches * waitForUpInstancesTask.execute(_) >> SUCCESS

    then:
    1 * completeParallelDeployTask.execute(_) >> SUCCESS

    where:
    regions << [["us-east-1"], ["us-east-1", "us-west-2"]]
    branches = regions.size()
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  @Unroll
  def "deploy stages will run tasks from a strategy"() {
    given:
    def stage = new Stage<>(execution, "deploy", "deploy", deployStageContext("prod", "test", *regions))
    execution.stages << stage

    and:
    executionRepository.retrievePipeline(execution.id) >> execution

    and:
    @Subject runner = create()

    when:
    runner.start(execution)

    then:
    branches * determineSourceServerGroupTask.execute(_) >> SUCCESS
    branches * determineHealthProvidersTask.execute(_) >> SUCCESS

    and:
    branches * createServerGroupTask.execute(_) >> SUCCESS
    branches * monitorKatoTask.execute(_) >> SUCCESS
    (2 * branches) * serverGroupCacheForceRefreshTask.execute(_) >> SUCCESS
    branches * waitForUpInstancesTask.execute(_) >> SUCCESS

    and:
    branches * testTask.execute(_) >> SUCCESS

    then:
    1 * completeParallelDeployTask.execute(_) >> SUCCESS

    where:
    regions << [["us-east-1"], ["us-east-1", "us-west-2"]]
    branches = regions.size()
    execution = Pipeline.builder().withId("1").withParallel(true).build()
  }

  Map deployStageContext(String account, String strategy, String... availabilityZones) {
    def context = ["account": account, restrictedExecutionWindow: [:], application: "whatever"]
    if (availabilityZones.size() == 1) {
      context.cluster = ["availabilityZones": [(availabilityZones[0]): []]]
    } else {
      context.clusters = availabilityZones.collect { region ->
        ["availabilityZones": [(region): ["a", "b", "c"].collect {
          "$region$it".toString()
        }]]
      }
    }
    context.strategy = strategy
    context[Stage.STAGE_TIMEOUT_OVERRIDE_KEY] = 3000
    return context
  }

  @CompileStatic
  static class TestStrategy implements Strategy {
    def <T extends Execution<T>> List<Stage<T>> composeFlow(Stage<T> stage) {
      return [newStage(stage.execution, "test", "test", [:], stage, STAGE_AFTER)]
    }

    @Override
    String getName() {
      return "test"
    }
  }

  @CompileStatic
  static class TestStage implements StageDefinitionBuilder {
    def <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
      builder.withTask("test", ExecutionRunnerSpec.TestTask)
    }
  }

  @CompileStatic
  static class Config {
    @Bean
    FactoryBean<FeaturesService> featuresService() {
      new SpockMockFactoryBean(FeaturesService)
    }

    @Bean
    FactoryBean<ExceptionHandler> exceptionHandler() {
      new SpockMockFactoryBean(ExceptionHandler)
    }

    @Bean
    FactoryBean<Sleeper> sleeper() { new SpockMockFactoryBean(Sleeper) }

    @Bean
    FactoryBean<ExecutionRepository> executionRepository() {
      new SpockMockFactoryBean(ExecutionRepository)
    }

    @Bean
    FactoryBean<CompleteParallelDeployTask> completeParallelDeployTask() {
      new SpockMockFactoryBean(CompleteParallelDeployTask)
    }

    // tasks defined by CreateServerGroupStage

    @Bean
    FactoryBean<DetermineSourceServerGroupTask> determineSourceServerGroupTask() {
      new SpockMockFactoryBean(DetermineSourceServerGroupTask)
    }

    @Bean
    FactoryBean<DetermineHealthProvidersTask> determineHealthProvidersTaskFactoryBean() {
      new SpockMockFactoryBean(DetermineHealthProvidersTask)
    }

    @Bean
    FactoryBean<CreateServerGroupTask> createServerGroupTask() {
      new SpockMockFactoryBean(CreateServerGroupTask)
    }

    @Bean
    FactoryBean<MonitorKatoTask> monitorKatoTask() {
      new SpockMockFactoryBean(MonitorKatoTask)
    }

    @Bean
    FactoryBean<ServerGroupCacheForceRefreshTask> serverGroupCacheForceRefreshTask() {
      new SpockMockFactoryBean(ServerGroupCacheForceRefreshTask)
    }

    @Bean
    FactoryBean<WaitForUpInstancesTask> waitForUpInstancesTask() {
      new SpockMockFactoryBean(WaitForUpInstancesTask)
    }

    // tasks defined by TestStage

    @Bean
    FactoryBean<ExecutionRunnerSpec.TestTask> testTask() {
      new SpockMockFactoryBean(ExecutionRunnerSpec.TestTask)
    }

    @Bean
    ContextParameterProcessor contextParameterProcessor() {
      new ContextParameterProcessor()
    }
  }
}

@ContextConfiguration(
  classes = [
    BatchTestConfiguration, TaskTaskletAdapterImpl,
    SpringBatchExecutionListenerProvider, Config
  ]
)
class SpringBatchDeploymentStrategiesExecutionSpec extends DeploymentStrategiesExecutionSpec<SpringBatchExecutionRunner> {

  // TODO: this is identical to SpringBatchExecutionRunnerSpec — can this be common — trait?
  @Override
  SpringBatchExecutionRunner create(StageDefinitionBuilder... stageDefBuilders) {
    applicationContext.with {
      stageDefBuilders.each {
        beanFactory.autowireBean(it)
        beanFactory.registerSingleton(it.type, it)
      }
      autowireCapableBeanFactory.createBean(SpringBatchExecutionRunner)
    }
  }
}
