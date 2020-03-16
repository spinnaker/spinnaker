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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.kato.tasks.ResizeAsgTask
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

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
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    if (!stage.parentStageId || stage.execution.stages.find {
      it.id == stage.parentStageId
    }.type != stage.type) {
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
  void beforeStages(@Nonnull StageExecution parentStage, @Nonnull StageGraphBuilder graph) {
    if (!parentStage.parentStageId || parentStage.execution.stages.find {
      it.id == parentStage.parentStageId
    }.type != parentStage.type) {
      // configure iff this stage has no parent or has a parent that is not a ResizeAsg stage

      List<StageExecution> stages = new ArrayList<>()
      def targetReferences = targetReferenceSupport.getTargetAsgReferences(parentStage)

      targetReferences.each { targetReference ->
        def context = [
          credentials: parentStage.context.credentials,
          regions    : [targetReference.region]
        ]

        if (targetReferenceSupport.isDynamicallyBound(parentStage)) {
          context.remove("asgName")
          context.target = parentStage.context.target
        } else {
          context.asgName = targetReference.asg.name
        }

        stages << StageExecutionFactory.newStage(
          parentStage.execution,
          modifyScalingProcessStage.getType(),
          "resumeScalingProcesses",
          context + [action: "resume", processes: ["Launch", "Terminate"]],
          parentStage,
          SyntheticStageOwner.STAGE_BEFORE
        )
      }

      if (targetReferenceSupport.isDynamicallyBound(parentStage)) {
        stages << StageExecutionFactory.newStage(
          parentStage.execution,
          determineTargetReferenceStage.type,
          "determineTargetReferences",
          parentStage.context,
          parentStage,
          SyntheticStageOwner.STAGE_BEFORE
        )
      }

      stages.forEach({graph.append(it)})
    }
  }

  @Override
  void afterStages(@Nonnull StageExecution parentStage, @Nonnull StageGraphBuilder graph) {
    if (!parentStage.parentStageId || parentStage.execution.stages.find {
      it.id == parentStage.parentStageId
    }.type != parentStage.type) {
      // configure iff this stage has no parent or has a parent that is not a ResizeAsg stage
      List<StageExecution> stages = new ArrayList<>()

      def targetReferences = targetReferenceSupport.getTargetAsgReferences(parentStage)
      def descriptions = resizeSupport.createResizeStageDescriptors(parentStage, targetReferences)

      if (descriptions.size()) {
        for (description in descriptions) {
          stages << StageExecutionFactory.newStage(
            parentStage.execution,
            this.getType(),
            "resizeAsg",
            description,
            parentStage,
            SyntheticStageOwner.STAGE_AFTER
          )
        }
      }

      targetReferences.each { targetReference ->
        def context = [
          credentials: parentStage.context.credentials,
          regions    : [targetReference.region]
        ]

        if (targetReferenceSupport.isDynamicallyBound(parentStage)) {
          def resizeContext = new HashMap(parentStage.context)
          resizeContext.regions = [targetReference.region]
          context.remove("asgName")
          context.target = parentStage.context.target
          stages << StageExecutionFactory.newStage(
            parentStage.execution,
            this.getType(),
            "resizeAsg",
            resizeContext,
            parentStage,
            SyntheticStageOwner.STAGE_AFTER
          )
        } else {
          context.asgName = targetReference.asg.name
        }

        context.put("action", "suspend")

        stages << StageExecutionFactory.newStage(
          parentStage.execution,
          modifyScalingProcessStage.getType(),
          "suspendScalingProcesses",
          context,
          parentStage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }

      stages.forEach({graph.append(it)})
    }
  }
}
