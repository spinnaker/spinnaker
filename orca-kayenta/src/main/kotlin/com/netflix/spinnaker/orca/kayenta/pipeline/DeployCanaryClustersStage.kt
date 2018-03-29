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

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.FindImageFromClusterStage
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import com.netflix.spinnaker.orca.kayenta.model.canaryStage
import com.netflix.spinnaker.orca.kayenta.model.deployments
import com.netflix.spinnaker.orca.kayenta.model.regions
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class DeployCanaryClustersStage : StageDefinitionBuilder {

  companion object {
    @JvmStatic
    val STAGE_TYPE = "deployCanaryClusters"
  }

  override fun beforeStages(parent: Stage, graph: StageGraphBuilder) {
    parent.deployments.also { deployments ->
      // find image stage for the control cluster
      graph.add {
        it.type = FindImageFromClusterStage.PIPELINE_CONFIG_TYPE
        it.name = "Find baseline image"
        it.context = mapOf(
          "application" to deployments.baseline.application,
          "account" to deployments.baseline.account,
          "cluster" to deployments.baseline.cluster,
          "regions" to deployments.regions,
          "cloudProvider" to (deployments.baseline.cloudProvider ?: "aws")
        )
      }
      parent.canaryStage.also { canaryStage ->
        // deployment for the control cluster follows the find image
        graph.append {
          it.type = ParallelDeployStage.PIPELINE_CONFIG_TYPE
          it.name = "Deploy control cluster"
          it.context.putAll(canaryStage.mapTo("/deployments/control"))
          it.context["strategy"] = "highlander"
        }

        // deployment for the experiment cluster is branched separately, there
        // should be an upstream bake / find image that supplies the artifact
        graph.add {
          it.type = ParallelDeployStage.PIPELINE_CONFIG_TYPE
          it.name = "Deploy experiment cluster"
          it.context.putAll(canaryStage.mapTo("/deployments/experiment"))
          it.context["strategy"] = "highlander"
        }
      }
    }
  }
}
