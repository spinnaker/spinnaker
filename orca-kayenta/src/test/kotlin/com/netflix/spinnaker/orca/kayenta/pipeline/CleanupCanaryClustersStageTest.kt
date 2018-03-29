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

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
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
  val mapper = OrcaObjectMapper.newInstance()

  given("a canary deployment pipeline") {
    val baseline = mapOf(
      "application" to "spindemo",
      "account" to "prod",
      "cluster" to "spindemo-prestaging-prestaging"
    )
    val controlCluster = mapOf(
      "account" to "prod",
      "application" to "spindemo",
      "availabilityZones" to mapOf(
        "us-west-1" to listOf("us-west-1a", "us-west-1c")
      ),
      "capacity" to mapOf("desired" to 1, "max" to 1, "min" to 1),
      "cloudProvider" to "aws",
      "cooldown" to 10,
      "copySourceCustomBlockDeviceMappings" to true,
      "ebsOptimized" to false,
      "enabledMetrics" to listOf<Any>(),
      "freeFormDetails" to "prestaging-baseline",
      "healthCheckGracePeriod" to 600,
      "healthCheckType" to "EC2",
      "iamRole" to "spindemoInstanceProfile",
      "instanceMonitoring" to true,
      "instanceType" to "m3.large",
      "interestingHealthProviderNames" to listOf("Amazon"),
      "keyPair" to "nf-prod-keypair-a",
      "loadBalancers" to listOf<Any>(),
      "moniker" to mapOf(
        "app" to "spindemo",
        "cluster" to "spindemo-prestaging-prestaging-baseline",
        "detail" to "prestaging-baseline",
        "stack" to "prestaging"
      ),
      "provider" to "aws",
      "securityGroups" to listOf("sg-b575ded0", "sg-b775ded2", "sg-dbe43abf"),
      "spotPrice" to "",
      "stack" to "prestaging",
      "subnetType" to "internal (vpc0)",
      "suspendedProcesses" to listOf<Any>(),
      "tags" to mapOf<String, Any>(),
      "targetGroups" to listOf<Any>(),
      "targetHealthyDeployPercentage" to 100,
      "terminationPolicies" to listOf("Default"),
      "useAmiBlockDeviceMappings" to false,
      "useSourceCapacity" to false
    )
    val experimentCluster = mapOf(
      "account" to "prod",
      "application" to "spindemo",
      "availabilityZones" to mapOf(
        "us-west-1" to listOf("us-west-1a", "us-west-1c")
      ),
      "capacity" to mapOf("desired" to 1, "max" to 1, "min" to 1),
      "cloudProvider" to "aws",
      "cooldown" to 10,
      "copySourceCustomBlockDeviceMappings" to true,
      "ebsOptimized" to false,
      "enabledMetrics" to listOf<Any>(),
      "freeFormDetails" to "prestaging-canary",
      "healthCheckGracePeriod" to 600,
      "healthCheckType" to "EC2",
      "iamRole" to "spindemoInstanceProfile",
      "instanceMonitoring" to true,
      "instanceType" to "m3.large",
      "interestingHealthProviderNames" to listOf("Amazon"),
      "keyPair" to "nf-prod-keypair-a",
      "loadBalancers" to listOf<Any>(),
      "moniker" to mapOf(
        "app" to "spindemo",
        "cluster" to "spindemo-prestaging-prestaging-canary",
        "detail" to "prestaging-canary",
        "stack" to "prestaging"
      ),
      "provider" to "aws",
      "securityGroups" to listOf("sg-b575ded0", "sg-b775ded2", "sg-dbe43abf"),
      "spotPrice" to "",
      "stack" to "prestaging",
      "subnetType" to "internal (vpc0)",
      "suspendedProcesses" to listOf<Any>(),
      "tags" to mapOf<String, Any>(),
      "targetGroups" to listOf<Any>(),
      "targetHealthyDeployPercentage" to 100,
      "terminationPolicies" to listOf("Default"),
      "useAmiBlockDeviceMappings" to false,
      "useSourceCapacity" to false
    )
    val delayBeforeCleanup = Duration.ofHours(3)
    val pipeline = pipeline {
      stage {
        refId = "1"
        type = KayentaCanaryStage.STAGE_TYPE
        context["deployments"] = mapOf(
          "baseline" to baseline,
          "control" to controlCluster,
          "experiment" to experimentCluster,
          "delayBeforeCleanup" to delayBeforeCleanup.toString()
        )
        stage {
          refId = "1<1"
          type = DeployCanaryClustersStage.STAGE_TYPE
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
      beforeStages.named("Disable control cluster") {
        assertThat(type).isEqualTo(DisableClusterStage.STAGE_TYPE)
        assertThat(requisiteStageRefIds).isEmpty()

        assertThat(context["cloudProvider"]).isEqualTo("aws")
        assertThat(context["credentials"]).isEqualTo(controlCluster["account"])
        assertThat(context["cluster"]).isEqualTo((controlCluster["moniker"] as Map<String, Any>)["cluster"])
        assertThat(context["moniker"]).isEqualTo(mapper.convertValue<Moniker>(controlCluster["moniker"]!!))
        assertThat(context["regions"]).isEqualTo(setOf("us-west-1"))

        assertThat(context["remainingEnabledServerGroups"]).isEqualTo(0)
      }
      beforeStages.named("Disable experiment cluster") {
        assertThat(type).isEqualTo(DisableClusterStage.STAGE_TYPE)
        assertThat(requisiteStageRefIds).isEmpty()

        assertThat(context["cloudProvider"]).isEqualTo("aws")
        assertThat(context["credentials"]).isEqualTo(experimentCluster["account"])
        assertThat(context["cluster"]).isEqualTo((experimentCluster["moniker"] as Map<String, Any>)["cluster"])
        assertThat(context["moniker"]).isEqualTo(mapper.convertValue<Moniker>(experimentCluster["moniker"]!!))
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
          "Disable control cluster",
          "Disable experiment cluster"
        )
        assertThat(context["waitTime"]).isEqualTo(delayBeforeCleanup.seconds)
      }
    }

    it("finally destroys the clusters") {
      beforeStages.named("Cleanup control cluster") {
        assertThat(type).isEqualTo(ShrinkClusterStage.STAGE_TYPE)
        assertThat(
          requisiteStageRefIds
            .map(pipeline::stageByRef)
            .map(Stage::getName)
        ).containsExactly("Wait before cleanup")

        assertThat(context["cloudProvider"]).isEqualTo("aws")
        assertThat(context["credentials"]).isEqualTo(controlCluster["account"])
        assertThat(context["cluster"]).isEqualTo((controlCluster["moniker"] as Map<String, Any>)["cluster"])
        assertThat(context["moniker"]).isEqualTo(mapper.convertValue<Moniker>(controlCluster["moniker"]!!))
        assertThat(context["regions"]).isEqualTo(setOf("us-west-1"))

        assertThat(context["allowDeleteActive"]).isEqualTo(true)
        assertThat(context["shrinkToSize"]).isEqualTo(0)
      }

      beforeStages.named("Cleanup experiment cluster") {
        assertThat(type).isEqualTo(ShrinkClusterStage.STAGE_TYPE)
        assertThat(
          requisiteStageRefIds
            .map(pipeline::stageByRef)
            .map(Stage::getName)
        ).containsExactly("Wait before cleanup")

        assertThat(context["cloudProvider"]).isEqualTo("aws")
        assertThat(context["credentials"]).isEqualTo(experimentCluster["account"])
        assertThat(context["cluster"]).isEqualTo((experimentCluster["moniker"] as Map<String, Any>)["cluster"])
        assertThat(context["moniker"]).isEqualTo(mapper.convertValue<Moniker>(experimentCluster["moniker"]!!))
        assertThat(context["regions"]).isEqualTo(setOf("us-west-1"))

        assertThat(context["allowDeleteActive"]).isEqualTo(true)
        assertThat(context["shrinkToSize"]).isEqualTo(0)
      }
    }
  }
})
