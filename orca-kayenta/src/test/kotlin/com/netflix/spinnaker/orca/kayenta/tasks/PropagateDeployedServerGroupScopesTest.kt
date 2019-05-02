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

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import com.netflix.spinnaker.orca.kayenta.CanaryScopes
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryServerGroupsStage
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryServerGroupsStage.Companion.DEPLOY_CONTROL_SERVER_GROUPS
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryServerGroupsStage.Companion.DEPLOY_EXPERIMENT_SERVER_GROUPS
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it


object PropagateDeployedServerGroupScopesTest : Spek({
  val mort = mock<MortService> {
    on { getAccountDetails("foo") } doReturn mapOf("accountId" to "abc123")
    on { getAccountDetails("bar") } doReturn mapOf("accountId" to "def456")
  }

  val subject = PropagateDeployedServerGroupScopes(mort)
  val objectMapper = OrcaObjectMapper.newInstance()

  given("upstream experiment and control deploy stages") {
    val pipeline = pipeline {
      stage {
        refId = "1"
        type = DeployCanaryServerGroupsStage.STAGE_TYPE
        name = "deployCanaryClusters"

        stage {
          type = ParallelDeployStage.PIPELINE_CONFIG_TYPE
          name = DEPLOY_CONTROL_SERVER_GROUPS
          stage {
            type = CreateServerGroupStage.PIPELINE_CONFIG_TYPE
            context["deploy.server.groups"] = mapOf(
              "us-central1" to listOf("app-control-a-v000")
            )
            context["deploy.account.name"] = "foo"
          }

          stage {
            type = CreateServerGroupStage.PIPELINE_CONFIG_TYPE
            context["deploy.server.groups"] = mapOf(
              "us-central1" to listOf("app-control-b-v000")
            )
            context["deploy.account.name"] = "bar"
          }
        }

        stage {
          type = ParallelDeployStage.PIPELINE_CONFIG_TYPE
          name = DEPLOY_EXPERIMENT_SERVER_GROUPS

          stage {
            type = CreateServerGroupStage.PIPELINE_CONFIG_TYPE
            context["deploy.server.groups"] = mapOf(
              "us-central1" to listOf("app-experiment-a-v000")
            )
            context["deploy.account.name"] = "foo"
          }

          stage {
            type = CreateServerGroupStage.PIPELINE_CONFIG_TYPE
            context["deploy.server.groups"] = mapOf(
              "us-central1" to listOf("app-experiment-b-v000")
            )
            context["deploy.account.name"] = "bar"
          }
        }
      }
    }

    subject.execute(pipeline.stageByRef("1")).outputs["deployedServerGroups"]?.let { pairs ->
      it("summarizes deployments, joining experiment and control pairs") {
        objectMapper.convertValue<List<DeployedServerGroupPair>>(pairs).let {
          assertThat(it).containsExactlyInAnyOrder(
            DeployedServerGroupPair(
              experimentAccountId = "abc123",
              experimentScope = "app-experiment-a-v000",
              experimentLocation = "us-central1",
              controlAccountId = "abc123",
              controlScope = "app-control-a-v000",
              controlLocation = "us-central1"
            ),
            DeployedServerGroupPair(
              experimentAccountId = "def456",
              experimentScope = "app-experiment-b-v000",
              experimentLocation = "us-central1",
              controlScope = "app-control-b-v000",
              controlLocation = "us-central1",
              controlAccountId = "def456"
            )
          )
        }
      }
    } ?: fail("Task should output `deployedServerGroups`")
  }
})

internal data class DeployedServerGroupPair(
  val controlLocation: String,
  val controlScope: String,
  val controlAccountId: String?,
  val experimentLocation: String,
  val experimentScope: String,
  val experimentAccountId: String?
)
