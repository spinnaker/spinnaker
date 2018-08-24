/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kayenta.pipeline

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.time.Duration

internal object CleanupCanaryClustersStageTest : Spek({

  val subject = CleanupCanaryClustersStage()

  given("a canary deployment pipeline") {
    val baseline = mapOf(
      "application" to "spindemo",
      "account" to "prod",
      "cluster" to "spindemo-prestaging"
    )
    val controlServerGroupA = serverGroup {
      mapOf(
        "application" to "spindemo",
        "stack" to "prestaging",
        "freeFormDetails" to "baseline-a"
      )
    }
    val controlServerGroupB = serverGroup {
      mapOf(
        "application" to "spindemo",
        "stack" to "prestaging",
        "freeFormDetails" to "baseline-b"
      )
    }
    val experimentServerGroupA = serverGroup {
      mapOf(
        "application" to "spindemo",
        "stack" to "prestaging",
        "freeFormDetails" to "canary-a"
      )
    }
    val experimentServerGroupB = serverGroup {
      mapOf(
        "application" to "spindemo",
        "stack" to "prestaging",
        "freeFormDetails" to "canary-b"
      )
    }
    val delayBeforeCleanup = Duration.ofHours(3)
    val pipeline = pipeline {
      stage {
        refId = "1"
        type = KayentaCanaryStage.STAGE_TYPE
        context["deployments"] = mapOf(
          "baseline" to baseline,
          "serverGroupPairs" to listOf(
            mapOf(
              "control" to controlServerGroupA,
              "experiment" to experimentServerGroupA
            ),
            mapOf(
              "control" to controlServerGroupB,
              "experiment" to experimentServerGroupB
            )
          ),
          "delayBeforeCleanup" to delayBeforeCleanup.toString()
        )
        stage {
          refId = "1<1"
          type = DeployCanaryServerGroupsStage.STAGE_TYPE
        }
        stage {
          refId = "1>1"
          type = CleanupCanaryClustersStage.STAGE_TYPE
          syntheticStageOwner = STAGE_AFTER
        }
      }
    }
    val canaryCleanupStage = pipeline.stageByRef("1>1")

    val beforeStages = subject.beforeStages(canaryCleanupStage)

    it("first disables the control and experiment clusters") {
      beforeStages.named("Disable control cluster spindemo-prestaging-baseline-a") {
        assertThat(type).isEqualTo(DisableClusterStage.STAGE_TYPE)
        assertThat(requisiteStageRefIds).isEmpty()

        assertThat(context["cloudProvider"]).isEqualTo("aws")
        assertThat(context["credentials"]).isEqualTo(controlServerGroupA["account"])
        assertThat(context["cluster"]).isEqualTo("spindemo-prestaging-baseline-a")
        assertThat(context["regions"]).isEqualTo(setOf("us-west-1"))

        assertThat(context["remainingEnabledServerGroups"]).isEqualTo(0)
      }
      beforeStages.named("Disable experiment cluster spindemo-prestaging-canary-a") {
        assertThat(type).isEqualTo(DisableClusterStage.STAGE_TYPE)
        assertThat(requisiteStageRefIds).isEmpty()

        assertThat(context["cloudProvider"]).isEqualTo("aws")
        assertThat(context["credentials"]).isEqualTo(experimentServerGroupA["account"])
        assertThat(context["cluster"]).isEqualTo("spindemo-prestaging-canary-a")
        assertThat(context["regions"]).isEqualTo(setOf("us-west-1"))

        assertThat(context["remainingEnabledServerGroups"]).isEqualTo(0)
      }
    }

    it("waits after disabling the clusters") {
      beforeStages.named("Wait before cleanup") {
        assertThat(type).isEqualTo(WaitStage.STAGE_TYPE)
        assertThat(
          requisiteStageRefIds
            .map(pipeline::stageByRef)
            .map(Stage::getName)
        ).containsExactlyInAnyOrder(
          "Disable control cluster spindemo-prestaging-baseline-a",
          "Disable control cluster spindemo-prestaging-baseline-b",
          "Disable experiment cluster spindemo-prestaging-canary-a",
          "Disable experiment cluster spindemo-prestaging-canary-b"
        )
        assertThat(context["waitTime"]).isEqualTo(delayBeforeCleanup.seconds)
      }
    }

    it("finally destroys the clusters") {
      beforeStages.named("Cleanup control cluster spindemo-prestaging-baseline-a") {
        assertThat(type).isEqualTo(ShrinkClusterStage.STAGE_TYPE)
        assertThat(
          requisiteStageRefIds
            .map(pipeline::stageByRef)
            .map(Stage::getName)
        ).containsExactly("Wait before cleanup")

        assertThat(context["cloudProvider"]).isEqualTo("aws")
        assertThat(context["credentials"]).isEqualTo(controlServerGroupA["account"])
        assertThat(context["regions"]).isEqualTo(setOf("us-west-1"))
        assertThat(context["cluster"]).isEqualTo("spindemo-prestaging-baseline-a")
        assertThat(context["allowDeleteActive"]).isEqualTo(true)
        assertThat(context["shrinkToSize"]).isEqualTo(0)
      }

      beforeStages.named("Cleanup experiment cluster spindemo-prestaging-canary-a") {
        assertThat(type).isEqualTo(ShrinkClusterStage.STAGE_TYPE)
        assertThat(
          requisiteStageRefIds
            .map(pipeline::stageByRef)
            .map(Stage::getName)
        ).containsExactly("Wait before cleanup")

        assertThat(context["cloudProvider"]).isEqualTo("aws")
        assertThat(context["credentials"]).isEqualTo(experimentServerGroupA["account"])
        assertThat(context["cluster"]).isEqualTo("spindemo-prestaging-canary-a")
        assertThat(context["regions"]).isEqualTo(setOf("us-west-1"))
        assertThat(context["allowDeleteActive"]).isEqualTo(true)
        assertThat(context["shrinkToSize"]).isEqualTo(0)
      }
    }
  }
})
