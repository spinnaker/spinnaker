/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.kato.tasks.ResizeAsgTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage

@Component
@CompileStatic
@Deprecated
class ResizeAsgStage implements StageDefinitionBuilder {
  static final String PIPELINE_CONFIG_TYPE = "resizeAsg"

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  ResizeSupport resizeSupport

  @Autowired
  ModifyScalingProcessStage modifyScalingProcessStage

  @Autowired
  DetermineTargetReferenceStage determineTargetReferenceStage

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    if (!stage.parentStageId || stage.execution.stages.find {
      it.id == stage.parentStageId
    }.type != stage.type) {
      stage.initializationStage = true

      // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
      stage.status = ExecutionStatus.SUCCEEDED
      return
    }

    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask)
      .withTask("resizeAsg", ResizeAsgTask)
      .withTask("monitorAsg", MonitorKatoTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      .withTask("waitForCapacityMatch", WaitForCapacityMatchTask)
  }

  @Override
  <T extends Execution> List<Stage<T>> aroundStages(Stage<T> parentStage) {
    if (!parentStage.parentStageId || parentStage.execution.stages.find {
      it.id == parentStage.parentStageId
    }.type != parentStage.type) {
      // configure iff this stage has no parent or has a parent that is not a ResizeAsg stage
      def stages = configureTargets(parentStage)
      if (targetReferenceSupport.isDynamicallyBound(parentStage)) {
        stages << newStage(
          parentStage.execution,
          determineTargetReferenceStage.type,
          "determineTargetReferences",
          parentStage.context,
          parentStage,
          SyntheticStageOwner.STAGE_BEFORE
        )
      }
      return stages
    }

    return []
  }

  @CompileDynamic
  private List<Stage> configureTargets(Stage stage) {
    def stages = []

    def targetReferences = targetReferenceSupport.getTargetAsgReferences(stage)
    def descriptions = resizeSupport.createResizeStageDescriptors(stage, targetReferences)

    if (descriptions.size()) {
      for (description in descriptions) {
        stages << newStage(
          stage.execution,
          this.getType(),
          "resizeAsg",
          description,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }
    }

    targetReferences.each { targetReference ->
      def context = [
        credentials: stage.context.credentials,
        regions    : [targetReference.region]
      ]

      if (targetReferenceSupport.isDynamicallyBound(stage)) {
        def resizeContext = new HashMap(stage.context)
        resizeContext.regions = [targetReference.region]
        context.remove("asgName")
        context.target = stage.context.target
        stages << newStage(
          stage.execution,
          this.getType(),
          "resizeAsg",
          resizeContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      } else {
        context.asgName = targetReference.asg.name
      }

      stages << newStage(
        stage.execution,
        modifyScalingProcessStage.getType(),
        "resumeScalingProcesses",
        context + [action: "resume", processes: ["Launch", "Terminate"]],
        stage,
        SyntheticStageOwner.STAGE_BEFORE
      )

      stages << newStage(
        stage.execution,
        modifyScalingProcessStage.getType(),
        "suspendScalingProcesses",
        context + [action: "suspend"],
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }
}
