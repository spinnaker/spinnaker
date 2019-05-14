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
import com.netflix.spinnaker.orca.ext.withTask
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import com.netflix.spinnaker.orca.kayenta.model.controlServerGroups
import com.netflix.spinnaker.orca.kayenta.model.deployments
import com.netflix.spinnaker.orca.kayenta.model.experimentServerGroups
import com.netflix.spinnaker.orca.kayenta.model.regions
import com.netflix.spinnaker.orca.kayenta.tasks.PropagateDeployedServerGroupScopes
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class DeployCanaryServerGroupsStage : StageDefinitionBuilder {

  companion object {
    @JvmStatic
    val STAGE_TYPE = "deployCanaryServerGroups"
    const val DEPLOY_CONTROL_SERVER_GROUPS = "Deploy control server groups"
    const val DEPLOY_EXPERIMENT_SERVER_GROUPS = "Deploy experiment server groups"
  }

  override fun beforeStages(parent: Stage, graph: StageGraphBuilder) {
    parent.deployments.also { deployments ->
      // find image stage for the control server groups
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

      // deployment for the control server groups follows the find image
      graph.append {
        it.type = ParallelDeployStage.PIPELINE_CONFIG_TYPE
        it.name = DEPLOY_CONTROL_SERVER_GROUPS
        it.context["clusters"] = parent.controlServerGroups
        it.context["strategy"] = "highlander"
      }

      // deployment for the experiment server groups is branched separately, there
      // should be an upstream bake / find image that supplies the artifact
      graph.add {
        it.type = ParallelDeployStage.PIPELINE_CONFIG_TYPE
        it.name = DEPLOY_EXPERIMENT_SERVER_GROUPS
        it.context["clusters"] = parent.experimentServerGroups
        it.context["strategy"] = "highlander"
      }
    }
  }

  override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
    builder.withTask<PropagateDeployedServerGroupScopes>("propagateDeployedServerGroupScopes")
  }
}
