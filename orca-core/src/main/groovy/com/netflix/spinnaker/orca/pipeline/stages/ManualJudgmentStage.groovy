/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.stages

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
class ManualJudgmentStage extends LinearStage {
  private static final String MAYO_CONFIG_NAME = "manualJudgment"

  ManualJudgmentStage() {
    super(MAYO_CONFIG_NAME)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [buildStep(stage, "waitForJudgment", WaitForManualJudgmentTask)]
  }

  @Component
  @VisibleForTesting
  public static class WaitForManualJudgmentTask implements RetryableTask {
    long backoffPeriod = 1000
    long timeout = Long.MAX_VALUE

    @Override
    TaskResult execute(Stage stage) {
      def judgmentStatus = (stage.context.judgmentStatus as String) ?: ""
      switch (judgmentStatus.toLowerCase()) {
        case "continue":
          return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
        case "stop":
          return new DefaultTaskResult(ExecutionStatus.TERMINAL)
      }

      return new DefaultTaskResult(ExecutionStatus.RUNNING)
    }
  }
}
