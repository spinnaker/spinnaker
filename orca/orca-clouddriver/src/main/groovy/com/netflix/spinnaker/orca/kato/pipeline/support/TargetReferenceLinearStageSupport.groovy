/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.kato.pipeline.DetermineTargetReferenceStage
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.Nonnull

@Deprecated
abstract class TargetReferenceLinearStageSupport implements StageDefinitionBuilder {
  @Autowired
  ObjectMapper objectMapper

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  DetermineTargetReferenceStage determineTargetReferenceStage

  @Override
  void beforeStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
    List<StageExecution> stages = composeTargets(parent)

    stages
      .findAll({ it.getSyntheticStageOwner() == SyntheticStageOwner.STAGE_BEFORE })
      .forEach({ graph.append(it) })
  }

  @Override
  void afterStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
    List<StageExecution> stages = composeTargets(parent)

    stages
      .findAll({ it.getSyntheticStageOwner() == SyntheticStageOwner.STAGE_AFTER })
      .forEach({ graph.append(it) })
  }

  List<StageExecution> composeTargets(StageExecution stage) {
    stage.resolveStrategyParams()
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      return composeDynamicTargets(stage)
    }

    return composeStaticTargets(stage)
  }

  private List<StageExecution> composeStaticTargets(StageExecution stage) {
    def descriptionList = buildStaticTargetDescriptions(stage)
    if (descriptionList.empty) {
      throw new TargetReferenceNotFoundException("Could not find any server groups for specified target")
    }
    def first = descriptionList.remove(0)
    stage.context.putAll(first)

    if (descriptionList.size()) {
      return descriptionList.collect {
        StageExecutionFactory.newStage(stage.execution, this.type, this.type, it, stage, SyntheticStageOwner.STAGE_AFTER)
      }
    }
    return []
  }

  private List<Map<String, Object>> buildStaticTargetDescriptions(StageExecution stage) {
    List<TargetReference> targets = targetReferenceSupport.getTargetAsgReferences(stage)

    return targets.collect { TargetReference target ->
      def region = target.region
      def asg = target.asg

      def description = new HashMap(stage.context)

      description.asgName = asg.name
      description.serverGroupName = asg.name
      description.region = region

      return description
    }
  }

  private List<StageExecution> composeDynamicTargets(StageExecution stage) {
    def stages = []

    // We only want to determine the target ASGs once per stage, so only inject if this is the root stage, i.e.
    // the one the user configured
    // This may become a bad assumption, or a limiting one, in that we cannot inject a dynamic stage ourselves
    // as part of some other stage that is not itself injecting a determineTargetReferences stage
    if (!stage.parentStageId) {
      def configuredRegions = stage.context.regions
      Map injectedContext = new HashMap(stage.context)
      injectedContext.regions = new ArrayList(configuredRegions)
      stages << StageExecutionFactory.newStage(
        stage.execution,
        determineTargetReferenceStage.type,
        "determineTargetReferences",
        injectedContext,
        stage,
        SyntheticStageOwner.STAGE_BEFORE
      )

      if (configuredRegions.size() > 1) {
        stage.context.region = configuredRegions.remove(0)
        for (region in configuredRegions) {
          def description = new HashMap(stage.context)
          description.region = region
          stages << StageExecutionFactory.newStage(
            stage.execution, this.type, this.type, description, stage, SyntheticStageOwner.STAGE_AFTER
          )
        }
      }
    }

    return stages
  }
}
