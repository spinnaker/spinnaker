/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryServerGroupsStage.Companion.DEPLOY_CONTROL_SERVER_GROUPS
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryServerGroupsStage.Companion.DEPLOY_EXPERIMENT_SERVER_GROUPS
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class PropagateDeployedServerGroupScopes(
  private val mortService: MortService
) : Task {

  private fun findAccountId(accountName: String): String? {
    val details = mortService.getAccountDetails(accountName)
    val accountId = details["accountId"] as String?
    return accountId
  }

  override fun execute(stage: Stage): TaskResult {
    val serverGroupPairs =
      stage.childrenOf(DEPLOY_CONTROL_SERVER_GROUPS) zip stage.childrenOf(DEPLOY_EXPERIMENT_SERVER_GROUPS)

    val scopes = serverGroupPairs.map { (control, experiment) ->
      val scope = mutableMapOf<String, Any?>()
      val controlContext = control.mapTo<DeployServerGroupContext>()
      controlContext.deployServerGroups.entries.first().let { (location, serverGroups) ->
        scope["controlLocation"] = location
        scope["controlScope"] = serverGroups.first()
      }
      scope["controlAccountId"] = findAccountId(controlContext.accountName)
      val experimentContext = experiment.mapTo<DeployServerGroupContext>()
      experimentContext.deployServerGroups.entries.first().let { (location, serverGroups) ->
        scope["experimentLocation"] = location
        scope["experimentScope"] = serverGroups.first()
      }
      scope["experimentAccountId"] = findAccountId(experimentContext.accountName)
      scope
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
      .outputs(mapOf("deployedServerGroups" to scopes))
      .build()
  }
}


private fun Stage.childrenOf(name: String): List<Stage> {
  val stage = execution.stages.find {
    it.name == name
      && it.topLevelStage == topLevelStage
  } ?: throw IllegalArgumentException("Could not find stage $name.")

  return execution.stages.filter {
    it.parentStageId == stage.id
  }
}


data class DeployServerGroupContext @JsonCreator constructor(
  @param:JsonProperty("deploy.server.groups") val deployServerGroups: Map<String, List<String>>,
  @param:JsonProperty("deploy.account.name") val accountName: String
)
