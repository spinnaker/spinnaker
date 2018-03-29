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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kayenta.model.KayentaCanaryContext
import com.netflix.spinnaker.orca.kayenta.pipeline.CleanupCanaryClustersStage
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryClustersStage
import com.netflix.spinnaker.orca.kayenta.pipeline.KayentaCanaryStage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

internal object PropagateDeployedClusterScopeTaskTest : Spek({

  val subject = PropagateDeployedClusterScopeTask()
  val mapper = OrcaObjectMapper.newInstance()

  given("a canary deployment pipeline") {

    val controlClusterName = "spindemo-prestaging-prestaging-baseline"
    val controlClusterRegion = "us-west-1"
    val experimentClusterName = "spindemo-prestaging-prestaging-canary"
    val experimentClusterRegion = "us-west-1"

    val baseline = mapOf(
      "application" to "spindemo",
      "account" to "prod",
      "cluster" to "spindemo-prestaging-prestaging"
    )
    val controlCluster = mapOf(
      "account" to "prod",
      "application" to "spindemo",
      "availabilityZones" to mapOf(
        controlClusterRegion to listOf("us-west-1a", "us-west-1c")
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
        "cluster" to controlClusterName,
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
        experimentClusterRegion to listOf("us-west-1a", "us-west-1c")
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
        "cluster" to experimentClusterName,
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
    val pipeline = pipeline {
      stage {
        refId = "1"
        type = KayentaCanaryStage.STAGE_TYPE
        context["deployments"] = mapOf(
          "baseline" to baseline,
          "control" to controlCluster,
          "experiment" to experimentCluster
        )
        stage {
          refId = "1<1"
          type = DeployCanaryClustersStage.STAGE_TYPE
        }
        stage {
          refId = "1>1"
          type = CleanupCanaryClustersStage.STAGE_TYPE
          syntheticStageOwner = SyntheticStageOwner.STAGE_AFTER
        }
      }
    }
    val canaryStage = pipeline.stageByRef("1")
    val canaryDeployStage = pipeline.stageByRef("1<1")


    given("the parent canary stage does not have a matching cluster scope") {
      beforeGroup {
        canaryStage.context["canaryConfig"] = mapOf(
          "canaryConfigId" to "MySampleStackdriverCanaryConfig",
          "scopes" to emptyList<Map<String, Any>>(),
          "scoreThresholds" to mapOf("marginal" to 75, "pass" to 90),
          "beginCanaryAnalysisAfterMins" to "0"
        )
      }

      it("updates the canary context with a new scope") {
        subject.execute(canaryDeployStage).let { result ->
          result.context["canaryConfig"]?.let {
            mapper.convertValue<KayentaCanaryContext>(it)
          }?.let { updatedCanaryContext ->
            assertThat(updatedCanaryContext.scopes.size).isEqualTo(1)
            updatedCanaryContext.scopes.first().let { scope ->
              assertThat(scope.controlScope).isEqualTo(controlClusterName)
              assertThat(scope.controlLocation).isEqualTo(controlClusterRegion)
              assertThat(scope.experimentScope).isEqualTo(experimentClusterName)
              assertThat(scope.experimentLocation).isEqualTo(experimentClusterRegion)
              assertThat(scope.extendedScopeParams).containsEntry("type", "cluster")
            }
          } ?: fail("Task should have output an updated canary context")
        }
      }
    }

    given("the parent canary stage has a matching cluster scope") {
      beforeGroup {
        canaryStage.context["canaryConfig"] = mapOf(
          "canaryConfigId" to "MySampleStackdriverCanaryConfig",
          "scopes" to listOf(mapOf(
            "controlScope" to controlClusterName,
            "controlLocation" to controlClusterRegion,
            "experimentScope" to experimentClusterName,
            "experimentLocation" to experimentClusterRegion
          )),
          "scoreThresholds" to mapOf("marginal" to 75, "pass" to 90),
          "beginCanaryAnalysisAfterMins" to "0"
        )
      }

      on("executing the task") {
      }

      it("does not add an extra scope") {
        subject.execute(canaryDeployStage).let { result ->
          assertThat(result.context).isEmpty()
        }
      }
    }
  }
})
