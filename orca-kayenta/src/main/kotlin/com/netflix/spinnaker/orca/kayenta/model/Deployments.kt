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

package com.netflix.spinnaker.orca.kayenta.model

import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryServerGroupsStage
import com.netflix.spinnaker.orca.kayenta.pipeline.KayentaCanaryStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import java.time.Duration

/**
 * Defines the deployment context for canary control and experiment server groups.
 */
internal data class Deployments(
  val baseline: Baseline,
  val serverGroupPairs: List<ServerGroupPair>,
  val delayBeforeCleanup: Duration = Duration.ZERO
)

/**
 * Gets the set of regions from the canary server group pairs. Can be specified
 * just as `region` or as `availabilityZones` which is a map of region to
 * zones.
 */
internal val Deployments.regions: Set<String>
  get() = serverGroupPairs.flatMap {
    it.control.regions + it.experiment.regions
  }.toSet()

/**
 * A control / experiment pair to be deployed.
 */
internal data class ServerGroupPair(
  val control: ServerGroupSpec,
  val experiment: ServerGroupSpec
)

/**
 * The source cluster for the canary control.
 */
internal data class Baseline(
  val cloudProvider: String?,
  val application: String,
  val account: String,
  val cluster: String
)

/**
 * A typed subset of a full server group context.
 */
internal data class ServerGroupSpec(
  val cloudProvider: String,
  val account: String,
  val availabilityZones: Map<String, Set<String>>?,
  val region: String?,
  val moniker: Moniker?,
  // TODO(dpeach): most providers don't support Moniker for deploy operations.
  // Remove app-stack-detail when they do.
  val application: String?,
  val stack: String?,
  val freeFormDetails: String?
)

/**
 * Gets cluster name from either the spec's moniker
 * or app-stack-detail combination.
 */
internal val ServerGroupSpec.cluster: String
  get() = when {
    moniker != null -> moniker.cluster
    application != null -> {
      val builder = AutoScalingGroupNameBuilder()
      builder.appName = application
      builder.stack = stack
      builder.detail = freeFormDetails
      builder.buildGroupName()
    }
    else -> throw IllegalArgumentException("Could not resolve server group name: ($this).")
  }

internal val ServerGroupSpec.regions: Set<String>
  get() = when {
    (availabilityZones?.isNotEmpty() ?: false) -> availabilityZones?.keys ?: emptySet()
    region != null -> setOf(region)
    else -> throw IllegalArgumentException("Could not resolve regions: ($this).")
  }

/**
 * Gets [Deployments] from the parent canary stage of this stage.
 */
internal val Stage.deployments: Deployments
  get() = canaryStage.mapTo("/deployments")

/**
 * Gets the control server groups' untyped contexts.
 */
internal val Stage.controlServerGroups: List<Any?>
  get() = serverGroupPairContexts.map { it["control"] }

/**
 * Gets the experiment server groups' untyped contexts.
 */
internal val Stage.experimentServerGroups: List<Any?>
  get() = serverGroupPairContexts.map { it["experiment"] }

private val Stage.serverGroupPairContexts: List<Map<String, Any?>>
  get() = canaryStage.mapTo("/deployments/serverGroupPairs")

/**
 * Gets the parent canary stage of this stage. Throws an exception if it's
 * missing or the wrong type.
 */
internal val Stage.canaryStage: Stage
  get() = parent?.apply {
    if (type != KayentaCanaryStage.STAGE_TYPE) {
      throw IllegalStateException("${DeployCanaryServerGroupsStage.STAGE_TYPE} should be the child of a ${KayentaCanaryStage.STAGE_TYPE} stage")
    }
  }
    ?: throw IllegalStateException("${DeployCanaryServerGroupsStage.STAGE_TYPE} should be the child of a ${KayentaCanaryStage.STAGE_TYPE} stage")
