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
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.repeat.RepeatStatus
import static com.netflix.spinnaker.orca.ExecutionStatus.*

@Immutable(knownImmutables = ["exitStatus"])
@CompileStatic
class BatchStepStatus {
  RepeatStatus repeatStatus
  ExitStatus exitStatus
  BatchStatus batchStatus

  static BatchStepStatus mapResult(ExecutionStatus status) {
    switch (status) {
      case SUCCEEDED:
        return new BatchStepStatus(RepeatStatus.FINISHED, translate(status), BatchStatus.COMPLETED)
      case SUSPENDED:
        return new BatchStepStatus(RepeatStatus.FINISHED, translate(status), BatchStatus.STOPPED)
      case TERMINAL:
        return new BatchStepStatus(RepeatStatus.FINISHED, translate(status), BatchStatus.FAILED)
      case RUNNING:
      case PAUSED:
        return new BatchStepStatus(RepeatStatus.CONTINUABLE, translate(status), BatchStatus.STARTED)
      case CANCELED:
        return new BatchStepStatus(RepeatStatus.FINISHED, translate(status), BatchStatus.COMPLETED)
      case REDIRECT:
        return new BatchStepStatus(RepeatStatus.FINISHED, translate(status), BatchStatus.COMPLETED)
      case STOPPED:
        return new BatchStepStatus(RepeatStatus.FINISHED, translate(status), BatchStatus.COMPLETED)
      case SKIPPED:
        return new BatchStepStatus(RepeatStatus.FINISHED, translate(status), BatchStatus.COMPLETED)
      case FAILED_CONTINUE:
        return new BatchStepStatus(RepeatStatus.FINISHED, translate(status), BatchStatus.COMPLETED)
    }
  }

  static ExitStatus translate(ExecutionStatus status) {
    switch (status) {
      case NOT_STARTED:
      case RUNNING:
      case PAUSED:
        return ExitStatus.EXECUTING
      case SUSPENDED:
      case CANCELED:
      case STOPPED:
        return ExitStatus.STOPPED
      case SUCCEEDED:
      case FAILED_CONTINUE:
      case SKIPPED:
        return ExitStatus.COMPLETED
      case TERMINAL:
        return ExitStatus.FAILED
      case REDIRECT:
        return new ExitStatus("REDIRECT")
    }
  }
}
