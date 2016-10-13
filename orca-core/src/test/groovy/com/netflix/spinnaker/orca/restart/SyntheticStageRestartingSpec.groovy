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

package com.netflix.spinnaker.orca.restart

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.batch.core.Step
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static java.lang.System.currentTimeMillis

@ContextConfiguration(classes = [
  EmbeddedRedisConfiguration, JesqueConfiguration, BatchTestConfiguration,
  SpringBatchConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
  JobCompletionListener, TestConfiguration, TaskConfig, StageConfig
])
class SyntheticStageRestartingSpec extends Specification {

  @Autowired PipelineStarter pipelineStarter
  @Autowired ObjectMapper mapper
  @Autowired ExecutionRepository repository
  @Autowired JobCompletionListener jobCompletionListener

  @Autowired BeforeTask beforeTask
  @Autowired MainTask mainTask
  @Autowired AfterTask afterTask

  def "a previously run pipeline can be restarted and completed tasks are skipped"() {
    given:
    def pipeline = pipelineStarter.create(mapper.readValue(pipelineConfigFor("test"), Map))
    pipeline.stages[0].tasks << new DefaultTask(id: 2, name: "main", status: RUNNING,
      startTime: currentTimeMillis())
    pipeline.stages << new PipelineStage(pipeline, "before", "before", [:])
    pipeline.stages[1].id = pipeline.stages[0].id + "-1-before"
    pipeline.stages[1].syntheticStageOwner = STAGE_BEFORE
    pipeline.stages[1].parentStageId = pipeline.stages[0].id
    pipeline.stages[1].status = SUCCEEDED
    pipeline.stages[1].startTime = currentTimeMillis()
    pipeline.stages[1].endTime = currentTimeMillis()
    pipeline.stages[1].tasks << new DefaultTask(id: 2, name: "before", status: SUCCEEDED,
      startTime: currentTimeMillis(),
      endTime: currentTimeMillis())
    pipeline.stages << new PipelineStage(pipeline, "after", "after", [:])
    pipeline.stages[2].id = pipeline.stages[0].id + "-2-after"
    pipeline.stages[2].parentStageId = pipeline.stages[0].id
    pipeline.stages[2].syntheticStageOwner = STAGE_AFTER
    pipeline.stages[2].status = NOT_STARTED
    repository.store(pipeline)

    when:
    pipelineStarter.resume(pipeline)
    jobCompletionListener.await()

    then:
    repository.retrievePipeline(pipeline.id).status.toString() == SUCCEEDED.name()

    and:
    0 * beforeTask.execute(_)
    1 * mainTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    1 * afterTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
  }

  private String pipelineConfigFor(String... stages) {
    def config = [
      application: "app",
      name       : "my-pipeline",
      stages     : stages.collect { [type: it] }
    ]
    mapper.writeValueAsString(config)
  }

  @CompileStatic
  static class SimpleSyntheticStage extends LinearStage {

    private final Task task
    private final StageBuilder beforeStage
    private final StageBuilder afterStage

    SimpleSyntheticStage(String name, StageBuilder beforeStage, Task mainTask, StageBuilder afterStage) {
      super(name)
      this.task = mainTask
      this.beforeStage = beforeStage
      this.afterStage = afterStage
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      injectBefore(stage, "before", beforeStage, [:])
      injectAfter(stage, "after", afterStage, [:])
      [buildStep(stage, "main", task)]
    }
  }

  static class StandaloneStageBuilder extends LinearStage {
    private Task task

    StandaloneStageBuilder(String stageName, Task task) {
      super(stageName)
      this.task = task
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      return [buildStep(stage, type, task)]
    }
  }

  static interface BeforeTask extends Task {}

  static interface AfterTask extends Task {}

  static interface MainTask extends Task {}

  @Component
  @CompileStatic
  static class TaskConfig {
    @Bean
    FactoryBean<BeforeTask> beforeTask() {
      new SpockMockFactoryBean<>(BeforeTask)
    }

    @Bean
    FactoryBean<AfterTask> afterTask() { new SpockMockFactoryBean<>(AfterTask) }

    @Bean
    FactoryBean<MainTask> mainTask() { new SpockMockFactoryBean<>(MainTask) }
  }

  @Component
  @CompileStatic
  static class StageConfig {
    @Bean
    @Qualifier("beforeStage")
    StageBuilder beforeStage(BeforeTask beforeTask) {
      new StandaloneStageBuilder("before", beforeTask)
    }

    @Bean
    @Qualifier("afterStage")
    StageBuilder afterStage(AfterTask afterTask) {
      new StandaloneStageBuilder("after", afterTask)
    }

    @Bean
    StageBuilder testStage(@Qualifier("beforeStage") StageBuilder beforeStage,
                           @Qualifier("afterStage") StageBuilder afterStage,
                           MainTask mainTask) {
      new SimpleSyntheticStage("test", beforeStage, mainTask, afterStage)
    }
  }
}
