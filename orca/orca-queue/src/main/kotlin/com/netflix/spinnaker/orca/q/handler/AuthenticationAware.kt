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
    val account = this.context.getOrDefault("account", "unknown") as String
    val cloudProvider = this.context.getOrDefault("cloudProvider", "unknown") as String

    try {
      ExecutionContext.set(
        ExecutionContext(
          execution.application,
          currentUser?.user,
          execution.type.name.toLowerCase(),
          execution.id,
          this.id,
          execution.origin,
          account.toLowerCase(),
          cloudProvider.toLowerCase(),
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
      .firstOrNull {
        it.stageBuilder is AuthenticatedStage &&
        it.stage.isManualJudgmentType &&
        !it.stage.status.isSkipped &&
        it.stage.withPropagateAuthentication()
      }?.let{
        val authStage = it.stage
        return if(authStage != null) {
          (it.stageBuilder as AuthenticatedStage).authenticatedUser(authStage).orElse(null)
        } else null
      }
  }
}
