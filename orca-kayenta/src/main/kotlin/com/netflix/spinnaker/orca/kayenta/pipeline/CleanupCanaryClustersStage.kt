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
import com.netflix.spinnaker.orca.kayenta.model.Deployments
import com.netflix.spinnaker.orca.kayenta.model.deployments
import com.netflix.spinnaker.orca.kayenta.model.regions
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class CleanupCanaryClustersStage : StageDefinitionBuilder {
  companion object {
    @JvmStatic
    val STAGE_TYPE = "cleanupCanaryClusters"
  }

  override fun beforeStages(parent: Stage, graph: StageGraphBuilder) {
    val deployments = parent.deployments

    val disableControl = graph.add {
      it.type = DisableClusterStage.STAGE_TYPE
      it.name = "Disable control cluster"
      it.context.putAll(deployments.controlCluster)
      it.context["remainingEnabledServerGroups"] = 0
    }

    val disableExperiment = graph.add {
      it.type = DisableClusterStage.STAGE_TYPE
      it.name = "Disable experiment cluster"
      it.context.putAll(deployments.experimentCluster)
      it.context["remainingEnabledServerGroups"] = 0
    }

    // wait to allow time for manual inspection
    val waitStage = graph.connect(disableControl) {
      it.type = WaitStage.STAGE_TYPE
      it.name = "Wait before cleanup"
      it.context["waitTime"] = deployments.delayBeforeCleanup.seconds
    }
    graph.connect(disableExperiment, waitStage)

    // destroy control cluster
    graph.connect(waitStage) {
      it.type = ShrinkClusterStage.STAGE_TYPE
      it.name = "Cleanup control cluster"
      it.context.putAll(deployments.controlCluster)
      it.context["allowDeleteActive"] = true
      it.context["shrinkToSize"] = 0
    }

    // destroy experiment cluster
    graph.connect(waitStage) {
      it.type = ShrinkClusterStage.STAGE_TYPE
      it.name = "Cleanup experiment cluster"
      it.context.putAll(deployments.experimentCluster)
      it.context["allowDeleteActive"] = true
      it.context["shrinkToSize"] = 0
    }
  }

  private val Deployments.controlCluster
    get() = mapOf(
      "cloudProvider" to control.cloudProvider,
      "credentials" to control.account,
      "cluster" to control.moniker.cluster,
      "moniker" to control.moniker,
      "regions" to regions
    )

  private val Deployments.experimentCluster
    get() = mapOf(
      "cloudProvider" to experiment.cloudProvider,
      "credentials" to experiment.account,
      "cluster" to experiment.moniker.cluster,
      "moniker" to experiment.moniker,
      "regions" to regions
    )
}
