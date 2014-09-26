/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.monitoring

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.lifecycle.BatchExecutionSpec
import com.netflix.spinnaker.orca.batch.pipeline.TestStage
import com.netflix.spinnaker.orca.monitoring.PipelineMonitor
import org.springframework.batch.core.Job
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.flow.FlowJob
import org.springframework.batch.core.step.tasklet.TaskletStep
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import static com.netflix.spinnaker.orca.DefaultTaskResult.SUCCEEDED

class PipelineMonitoringSpec extends BatchExecutionSpec {

  def pipelineMonitor = Mock(PipelineMonitor)

  @Autowired AbstractApplicationContext applicationContext
  @Autowired StepBuilderFactory steps

  def task1 = Mock(Task)
  def task2 = Mock(Task)

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    new TestStage("foo", steps, pipelineMonitor, task1, task2)
      .build(jobBuilder)
      .build()
      .build()
  }

  def "can extract details of tasks"() {
    given: "the stage's tasks succeed"
    task1.execute(*_) >> SUCCEEDED
    task2.execute(*_) >> SUCCEEDED

    when: "the pipeline runs"
    launchJob()

    then:
    jobLauncherTestUtils.job instanceof FlowJob
    def steps = jobLauncherTestUtils.job.stepNames.collect {
      jobLauncherTestUtils.job.getStep(it)
    }
    steps.size() == 2
    steps.every { it instanceof TaskletStep }
    steps.every { ((TaskletStep) it).tasklet instanceof TaskTaskletAdapter }
  }

  def "a stage with multiple tasks raises a single begin and end stage event"() {
    given: "the stage's tasks succeed"
    task1.execute(*_) >> SUCCEEDED
    task2.execute(*_) >> SUCCEEDED

    when: "the pipeline runs"
    launchJob()

    then: "we get an event at the start of the stage"
    1 * pipelineMonitor.beginStage(stageName)

    then: "we get an event at the start and end of each task"
    2 * pipelineMonitor.beginTask()
    2 * pipelineMonitor.endTask(TaskResult.Status.SUCCEEDED)

    then: "we get an event at the end of the stage"
    1 * pipelineMonitor.endStage(stageName)

    where:
    stageName = "foo"
  }

  def "if a task fails an event is raised indicating the stage has failed"() {
    given: "a stage with a single task"
    task1.execute(*_) >> new DefaultTaskResult(TaskResult.Status.FAILED)

    when: "the pipeline runs"
    launchJob()

    then: "we get an event at the start of the stage"
    1 * pipelineMonitor.beginStage(stageName)

    then: "the second task never runs"
    0 * task2.execute(*_)

    and: "we get an event at the start and end of the first task"
    1 * pipelineMonitor.beginTask()
    1 * pipelineMonitor.endTask(TaskResult.Status.FAILED)

    then: "we don't get an event at the end of the stage"
    0 * pipelineMonitor.endStage(stageName)

    where:
    stageName = "foo"
  }

}
