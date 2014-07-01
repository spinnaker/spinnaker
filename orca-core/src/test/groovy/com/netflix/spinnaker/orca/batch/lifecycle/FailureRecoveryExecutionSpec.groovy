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
import static com.netflix.spinnaker.orca.TaskResult.Status.FAILED
import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED

class FailureRecoveryExecutionSpec extends BatchExecutionSpec {

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

  def "if the first task fails the recovery task is run"() {
    given:
    startTask.execute(_) >> new DefaultTaskResult(FAILED)

    when:
    def jobExecution = launchJob()

    then:
    1 * recoveryTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    1 * endTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    and:
    jobExecution.exitStatus == ExitStatus.COMPLETED
  }

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    new FailureRecoveryWorkflowBuilder(steps: steps, startTask: startTask, recoveryTask: recoveryTask, endTask: endTask)
        .build(jobBuilder)
        .build()
  }
}
