/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.lifecycle

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.step.AbstractStep

import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep

class LinearStageSpec extends AbstractBatchLifecycleSpec {
  def listeners = [new StageStatusPropagationListener(executionRepository)]

  def ctx1 = [a: 1]
  def ctx2 = [b: 2]

  def task1 = Mock(Task)
  def task2 = Mock(Task)
  def task3 = Mock(Task)

  void "should properly order stages and steps"() {
    setup:
    def pos = []

    when:
    launchJob()

    then:
    1 * task1.execute(_) >> { Stage stage ->
      assert ctx1 == stage.context
      pos << 1
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
    1 * task2.execute(_) >> { Stage stage ->
      pos << 2
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
    1 * task3.execute(_) >> { Stage stage ->
      assert ctx2 == stage.context
      pos << 3
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
    pos == [1, 2, 3]
  }

  void "should properly mark injected stages as synthetic"() {
    when:
    launchJob()

    then:
    1 * task1.execute(_) >> { Stage stage ->
      assert stage.syntheticStageOwner == Stage.SyntheticStageOwner.STAGE_BEFORE
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
    1 * task2.execute(_) >> { Stage stage ->
      assert !stage.syntheticStageOwner
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
    1 * task3.execute(_) >> { Stage stage ->
      assert stage.syntheticStageOwner == Stage.SyntheticStageOwner.STAGE_AFTER
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
  }

  void "should specify parent stage"() {
    when:
    launchJob()

    then:
    1 * task1.execute(_) >> { Stage stage ->
      assert stage.parentStageId == pipeline.stages[1].id
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
    1 * task2.execute(_) >> { Stage stage ->
      assert stage.parentStageId == null
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
    1 * task3.execute(_) >> { Stage stage ->
      assert stage.parentStageId == pipeline.stages[1].id
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }
  }

  void "parallel execution should only start a new flow path the first time a job builder is seen"() {
    given:
    def linearStage = new LinearStage("") {
      @Override
      List<Step> buildSteps(Stage stage) {
        return []
      }
    }
    def jobBuilder = Mock(FlowBuilder)
    def pipeline = new Pipeline()
    pipeline.parallel = true
    pipeline.stages << new PipelineStage(pipeline, "", [:])

    when:
    linearStage.wireSteps(jobBuilder, [], pipeline.stages[0])

    then:
    !pipeline.builtPipelineObjects.contains(jobBuilder)

    when:
    linearStage.wireSteps(jobBuilder, [buildStep("Step1"), buildStep("Step2")], pipeline.stages[0])

    then:
    pipeline.builtPipelineObjects.contains(jobBuilder)
    1 * jobBuilder.from(_)
    1 * jobBuilder.next(_)

    when:
    linearStage.wireSteps(jobBuilder, [buildStep("Step3"), buildStep("Step4")], pipeline.stages[0])

    then:
    2 * jobBuilder.next(_)
  }

  @Override
  Pipeline createPipeline() {
    Pipeline.builder().withStage("stage2").build()
  }

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    def stage = pipeline.namedStage("stage2")
    def builder = jobBuilder.flow(initializationStep(steps, pipeline))
    def stageBuilder = new InjectStageBuilder(steps, new TaskTaskletAdapter(executionRepository, []))
    stageBuilder.build(builder, stage).build().build()
  }

  class StandaloneStageBuilder extends LinearStage {
    private Task task
    StandaloneStageBuilder(String stageName, Task task) {
      super(stageName)
      setTaskListeners(listeners)
      this.task = task
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      return [buildStep(stage, "step", task)]
    }
  }

  private Step buildStep(String stepName) {
    new AbstractStep() {
      {
        setName(stepName)
      }

      @Override
      protected void doExecute(StepExecution stepExecution) throws Exception {
        stepExecution.status = BatchStatus.COMPLETED
      }
    }
  }

  class InjectStageBuilder extends LinearStage {

    StandaloneStageBuilder stageBuilder1 = new StandaloneStageBuilder("stage1", task1)
    StandaloneStageBuilder stageBuilder2 = new StandaloneStageBuilder("stage3", task3)

    InjectStageBuilder(StepBuilderFactory steps, TaskTaskletAdapter adapter) {
      super("stage2")
      setTaskListeners(listeners)
      setSteps(steps)
      setTaskTaskletAdapter(adapter)
      stageBuilder1.steps = steps
      stageBuilder1.taskTaskletAdapter = adapter
      stageBuilder2.steps = steps
      stageBuilder2.taskTaskletAdapter = adapter
    }

    @Override
    public List<Step> buildSteps(Stage stage) {
      injectBefore(stage, "before", stageBuilder1, ctx1)
      injectAfter(stage, "after", stageBuilder2, ctx2)
      [buildStep(stage, "myTask", task2)]
    }
  }
}
