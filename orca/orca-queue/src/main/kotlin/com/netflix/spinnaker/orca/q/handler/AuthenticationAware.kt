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

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.orca.AuthenticatedStage
import com.netflix.spinnaker.orca.ExecutionContext
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.security.RunAsTokenProvider
import com.netflix.spinnaker.security.AuthenticatedRequest
import java.util.concurrent.Callable
import org.apache.commons.lang3.StringUtils

interface AuthenticationAware {

  val stageNavigator: StageNavigator

  /**
   * Optional provider that re-issues the execution's identity token. When present, Orca asks the
   * token authority (Front50) at each stage boundary (here, where the authentication context is set
   * up) to issue a fresh, short-lived token for the subject + roles captured at admission, and
   * propagates it on [Header.IDENTITY_TOKEN]. So long executions never carry a use-expired token
   * while the subject and roles stay those admission granted (never re-resolved). Defaults to null
   * so implementors that don't wire it (and tests) keep the legacy behavior.
   */
  val runAsTokenProvider: RunAsTokenProvider?
    get() = null

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
        AuthenticatedRequest.runAs(
          currentUser.user,
          currentUser.allowedAccounts,
          true,
          Callable {
            propagateIdentityToken(currentUser)
            block()
          }
        ).call()
      } else {
        AuthenticatedRequest.propagate(
          {
            propagateIdentityToken(currentUser)
            block()
          },
          true
        ).call()
      }
    } finally {
      ExecutionContext.clear()
    }
  }

  /**
   * Stamp a fresh identity token onto [Header.IDENTITY_TOKEN] for this stage by re-issuing it from
   * the admission-time grant (subject + roles) captured on the execution. Best-effort: if no
   * provider is wired, no subject is present, or issuance fails, leave the legacy propagated
   * identity rather than failing an already-admitted execution.
   */
  private fun StageExecution.propagateIdentityToken(currentUser: PipelineExecution.AuthenticationDetails?) {
    val provider = runAsTokenProvider ?: return
    val auth = currentUser ?: return
    val subject = auth.user
    if (StringUtils.isBlank(subject)) {
      return
    }
    provider
      .issueExecutionToken(
        subject,
        auth.roles,
        auth.isAdmin,
        auth.isAccountManager
      )
      .ifPresent { AuthenticatedRequest.set(Header.IDENTITY_TOKEN, it) }
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
