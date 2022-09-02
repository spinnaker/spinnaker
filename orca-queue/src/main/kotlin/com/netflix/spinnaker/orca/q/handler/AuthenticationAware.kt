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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.AuthenticatedStage
import com.netflix.spinnaker.orca.ExecutionContext
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.apache.commons.lang3.StringUtils

interface AuthenticationAware {

  val stageNavigator: StageNavigator

  fun StageExecution.withAuth(block: () -> Unit) {
    val currentUser = retrieveAuthenticatedUser(this) ?: execution.authentication
    try {
      ExecutionContext.set(
        ExecutionContext(
          execution.application,
          currentUser?.user,
          execution.type.name.toLowerCase(),
          execution.id,
          this.id,
          execution.origin,
          this.startTime
        )
      )
      if (StringUtils.isNotBlank(currentUser?.user)) {
        AuthenticatedRequest.runAs(currentUser.user, currentUser.allowedAccounts, false, block).call()
      } else {
        AuthenticatedRequest.propagate(block, false).call()
      }
    } finally {
      ExecutionContext.clear()
    }
  }

  fun retrieveAuthenticatedUser(stage: StageExecution) : PipelineExecution.AuthenticationDetails? {
    return stageNavigator
      .ancestors(stage)
      .firstOrNull { it.stageBuilder is AuthenticatedStage } ?.let{
        (it.stageBuilder as AuthenticatedStage).authenticatedUser(solveSkippedStages(it.stage)).orElse(null)
      }
  }


  // When a first valid candidate is found in the ancestors chain is returned
  // until the ancestor chain was iterated completely at the pipeline beginning
  fun backtrackSkippedStages(stage: StageExecution): StageExecution {
    if (stage.isManualJudgmentType &&
      !stage.status.isSkipped &&
      stage.withPropagateAuthentication()) {
      return stage;
    }
    val previousStage = if (stageNavigator.ancestors(stage).size > 1) stageNavigator.ancestors(stage).get(1).stage else null
    return if (previousStage == null) stage else backtrackSkippedStages(previousStage)
  }

  //Next method will look by a possible stage with authentication propagated in case that previous
  //stage was skipped, iterating the stage ancestors. By the moment only MJ stages approved with
  //auth propagated are considerated as candidates
  fun solveSkippedStages(stage: StageExecution): StageExecution {
    if (stage.isManualJudgmentType() &&
      stage.status.isSkipped) {
      val result = backtrackSkippedStages(stage)
      stage.lastModified = result.lastModified
      return result
    }
    return stage
  }
}
