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

import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.model.CanaryConfigScope
import com.netflix.spinnaker.orca.kayenta.model.KayentaCanaryContext
import com.netflix.spinnaker.orca.kayenta.model.deployments
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class PropagateDeployedClusterScopeTask : Task {
  override fun execute(stage: Stage): TaskResult {
    val (_, control, experiment) = stage.deployments
    val canary = stage.parent?.mapTo<KayentaCanaryContext>("/canaryConfig")
      ?: throw IllegalStateException("No parent stage found")

    val deployedClusterScope = CanaryConfigScope(
      controlScope = control.moniker.cluster,
      controlLocation = control.region
        ?: control.availabilityZones.keys.firstOrNull(),
      experimentScope = experiment.moniker.cluster,
      experimentLocation = experiment.region
        ?: experiment.availabilityZones.keys.firstOrNull(),
      startTimeIso = null,
      endTimeIso = null,
      extendedScopeParams = mapOf("type" to "cluster")
    )

    return if (canary.scopes.find {
        it.controlScope == deployedClusterScope.controlScope &&
          it.controlLocation == deployedClusterScope.controlLocation &&
          it.experimentScope == deployedClusterScope.experimentScope &&
          it.experimentLocation == deployedClusterScope.experimentLocation
      } == null) {
      TaskResult(SUCCEEDED, mapOf(
        "canaryConfig" to canary.copy(scopes = canary.scopes + deployedClusterScope)
      ))
    } else {
      TaskResult.SUCCEEDED
    }
  }
}
