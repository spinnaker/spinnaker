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

import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.ext.withTask
import com.netflix.spinnaker.orca.kayenta.model.KayentaCanaryContext
import com.netflix.spinnaker.orca.kayenta.tasks.AggregateCanaryResultsTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class KayentaCanaryStage(private val clock: Clock) : StageDefinitionBuilder {

  override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
    builder.withTask<AggregateCanaryResultsTask>("aggregateCanaryResults")
  }

  override fun beforeStages(parent: Stage, graph: StageGraphBuilder) {
    if (parent.context["deployments"] != null) {
      graph.add {
        it.type = DeployCanaryServerGroupsStage.STAGE_TYPE
        it.name = "Deploy Canary Server Groups"
      }
    }

    val canaryConfig = parent.mapTo<KayentaCanaryContext>("/canaryConfig")

    if (canaryConfig.scopes.isEmpty()) {
      throw IllegalArgumentException("Canary stage configuration must contain at least one scope.")
    }

    // These are not used in this stage, but fail fast if they are missing as RunCanaryIntervalsStage
    // needs one of these.
    if (canaryConfig.endTime == null && canaryConfig.lifetime == null) {
      throw IllegalArgumentException("Canary stage configuration must include either `endTime` or `lifetimeDuration`.")
    }

    val runStage = graph.append {
      it.type = RunCanaryIntervalsStage.STAGE_TYPE
      it.name = "Run Canary Intervals"
      it.context["canaryConfig"] = canaryConfig
      it.context["continuePipeline"] = parent.context["continuePipeline"]
    }
    parent.context["intervalStageId"] = runStage.id
  }

  override fun afterStages(parent: Stage, graph: StageGraphBuilder) {
    if (parent.context["deployments"] != null) {
      graph.add {
        it.type = CleanupCanaryClustersStage.STAGE_TYPE
        it.name = "Cleanup Canary Clusters"
      }
    }
  }

  override fun onFailureStages(stage: Stage, graph: StageGraphBuilder) {
    afterStages(stage, graph)
  }

  override fun getType() = STAGE_TYPE

  companion object {
    @JvmStatic
    val STAGE_TYPE = "kayentaCanary"
  }
}

private val KayentaCanaryContext.endTime: Instant?
  get() = scopes.first().endTime

private val KayentaCanaryContext.startTime: Instant?
  get() = scopes.first().startTime

private val KayentaCanaryContext.step: Duration
  get() = Duration.ofSeconds(scopes.first().step)
