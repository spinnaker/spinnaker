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

import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryClustersStage
import com.netflix.spinnaker.orca.kayenta.pipeline.KayentaCanaryStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import java.time.Duration

/**
 * Defines the deployment context for canary control and experiment clusters.
 */
internal data class Deployments(
  val baseline: Baseline,
  val control: ClusterSpec,
  val experiment: ClusterSpec,
  val delayBeforeCleanup: Duration = Duration.ofHours(1)
)

/**
 * Gets the set of regions from the canary cluster specs. Can be specified
 * just as `region` or as `availabilityZones` which is a map of region to
 * zones.
 */
internal val Deployments.regions: Set<String>
  get() = if (experiment.availabilityZones.isNotEmpty()) {
    experiment.availabilityZones.keys + control.availabilityZones.keys
  } else {
    setOf(experiment.region, control.region).filterNotNull().toSet()
  }

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
 * The deployment context for a single cluster.
 */
internal data class ClusterSpec(
  val cloudProvider: String,
  val account: String,
  val moniker: Moniker,
  val availabilityZones: Map<String, Set<String>>,
  val region: String?
)

/**
 * Gets [Deployments] from the parent canary stage of this stage.
 */
internal val Stage.deployments: Deployments
  get() = canaryStage.mapTo("/deployments")

/**
 * Gets the parent canary stage of this stage. Throws an exception if it's
 * missing or the wrong type.
 */
internal val Stage.canaryStage: Stage
  get() = parent?.apply {
    if (type != KayentaCanaryStage.STAGE_TYPE) {
      throw IllegalStateException("${DeployCanaryClustersStage.STAGE_TYPE} should be the child of a ${KayentaCanaryStage.STAGE_TYPE} stage")
    }
  }
    ?: throw IllegalStateException("${DeployCanaryClustersStage.STAGE_TYPE} should be the child of a ${KayentaCanaryStage.STAGE_TYPE} stage")
