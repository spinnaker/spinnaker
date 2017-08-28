/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.mine.pipeline.DeployCanaryStage.CompleteDeployCanaryTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.FAILED_CONTINUE
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DeployCanaryStageSpec extends Specification {

  def "should short-circuit and return in a TERMINAL status if any deploy stages are not successful"() {
    setup:
    Pipeline pipeline = pipeline {
      stage {
        id = "a"
        type = "the stage"
      }
      stage {
        type = "deploy"
        parentStageId = "a"
        status = SUCCEEDED
      }
      stage {
        type = "deploy"
        parentStageId = "a"
        status = FAILED_CONTINUE
      }
    }
    CompleteDeployCanaryTask task = new CompleteDeployCanaryTask(Optional.empty(), null)

    expect:
    task.execute(pipeline.stageById("a")).status == ExecutionStatus.TERMINAL
  }
}
