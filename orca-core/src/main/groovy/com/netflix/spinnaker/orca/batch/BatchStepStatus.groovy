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

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import com.netflix.spinnaker.orca.TaskResult
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.repeat.RepeatStatus

@Immutable(knownImmutables = ["exitStatus"])
@CompileStatic
class BatchStepStatus {

  RepeatStatus repeatStatus
  ExitStatus exitStatus

  static BatchStepStatus mapResult(TaskResult result) {
    switch (result.status) {
      case TaskResult.Status.SUCCEEDED:
        return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.COMPLETED)
      case TaskResult.Status.SUSPENDED:
        return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.STOPPED)
      case TaskResult.Status.FAILED:
        return new BatchStepStatus(RepeatStatus.FINISHED, ExitStatus.FAILED)
      case TaskResult.Status.RUNNING:
        return new BatchStepStatus(RepeatStatus.CONTINUABLE, ExitStatus.EXECUTING)
    }
  }
}
