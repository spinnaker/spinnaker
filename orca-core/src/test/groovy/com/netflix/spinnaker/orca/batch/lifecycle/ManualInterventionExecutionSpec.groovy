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
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException
import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED
import static com.netflix.spinnaker.orca.TaskResult.Status.SUSPENDED

class ManualInterventionExecutionSpec extends BatchExecutionSpec {

  def preInterventionTask = Stub(Task)
  def postInterventionTask = Mock(Task)
  def finalTask = Mock(Task)

  def "workflow will stop if the first task suspends the job"() {
    given:
    preInterventionTask.execute(_) >> new DefaultTaskResult(SUSPENDED)

    when:
    launchJob()

    then:
    0 * postInterventionTask._
    0 * finalTask._
  }

  def "workflow will resume if the job is restarted"() {
    given:
    preInterventionTask.execute(_) >> new DefaultTaskResult(SUSPENDED)
    def jobExecution = launchJob()

    when:
    resumeJob jobExecution

    then:
    1 * postInterventionTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    1 * finalTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
  }

  def "can run to completion if the first step does not stop the job"() {
    given:
    preInterventionTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    when:
    launchJob()

    then:
    1 * postInterventionTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    1 * finalTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
  }

  def "a completed job cannot be restarted"() {
    given:
    preInterventionTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    postInterventionTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    finalTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    and:
    def jobExecution = launchJob()

    expect:
    jobExecution.exitStatus == ExitStatus.COMPLETED

    when:
    resumeJob jobExecution

    then:
    thrown JobInstanceAlreadyCompleteException
  }

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    new ManualInterventionWorkflowBuilder(steps: steps, preInterventionTask: preInterventionTask, postInterventionTask: postInterventionTask, finalTask: finalTask)
        .build(jobBuilder)
        .build()
  }
}
