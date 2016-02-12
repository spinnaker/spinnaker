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

package com.netflix.spinnaker.orca.batch.lifecycle

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder
import spock.lang.Ignore
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep

class FailureRecoveryExecutionSpec extends AbstractBatchLifecycleSpec {

  def startTask = Stub(Task)
  def recoveryTask = Mock(Task)
  def endTask = Mock(Task)

  def "if the first task completes normally the recovery task does not run"() {
    given:
    startTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    when:
    def jobExecution = launchJob()

    then:
    1 * endTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    and:
    0 * recoveryTask._

    and:
    jobExecution.exitStatus == ExitStatus.COMPLETED
  }

  @Ignore
  def "if a task is TERMINAL, the pipeline stops"() {
    given:
    startTask.execute(_) >> new DefaultTaskResult(TERMINAL)

    when:
    def jobExecution = launchJob()

    then:
    0 * recoveryTask.execute(_)
    0 * endTask.execute(_)

    and:
    jobExecution.exitStatus == ExitStatus.STOPPED
  }

  @Override
  Pipeline createPipeline() {
    Pipeline.builder().withStage("failureRecovery").build()
  }

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    def builder = jobBuilder.flow(initializationStep(steps, pipeline))
    new FailureRecoveryStage(
        steps: steps,
        startTask: startTask,
        recoveryTask: recoveryTask,
        endTask: endTask,
        taskTaskletAdapter: new TaskTaskletAdapter(executionRepository, [])
    ).build(builder, pipeline.namedStage("failureRecovery"))
     .build()
     .build()
  }
}
