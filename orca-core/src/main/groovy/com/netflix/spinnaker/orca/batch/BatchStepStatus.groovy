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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.repeat.RepeatStatus

@Immutable(knownImmutables = ["exitStatus"])
@CompileStatic
class BatchStepStatus {
  RepeatStatus repeatStatus
  ExitStatus exitStatus
  BatchStatus batchStatus

  static BatchStepStatus mapResult(TaskResult result) {
    switch (result.status) {
      case ExecutionStatus.SUCCEEDED:
        return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.COMPLETED, BatchStatus.COMPLETED)
      case ExecutionStatus.SUSPENDED:
        return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.STOPPED, BatchStatus.STOPPED)
      case ExecutionStatus.FAILED:
      case ExecutionStatus.TERMINAL:
        return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.FAILED, BatchStatus.FAILED)
      case ExecutionStatus.RUNNING:
        return new BatchStepStatus(RepeatStatus.CONTINUABLE, ExitStatus.EXECUTING, BatchStatus.STARTED)
      case ExecutionStatus.CANCELED:
        return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.STOPPED, BatchStatus.COMPLETED)
      case ExecutionStatus.REDIRECT:
        return new BatchStepStatus(RepeatStatus.FINISHED, new ExitStatus(ExecutionStatus.REDIRECT.name()) , BatchStatus.COMPLETED)
    }
  }
}
