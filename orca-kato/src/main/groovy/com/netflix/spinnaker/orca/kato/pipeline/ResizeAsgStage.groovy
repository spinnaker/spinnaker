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
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReference
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.ResizeAsgTask
import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ResizeAsgStage extends LinearStage {
  static final String PIPELINE_CONFIG_TYPE = "resizeAsg"

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  ModifyScalingProcessStage modifyScalingProcessStage

  @Autowired
  DetermineTargetReferenceStage determineTargetReferenceStage

  ResizeAsgStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    if (!stage.parentStageId || stage.execution.stages.find { it.id == stage.parentStageId}.type != stage.type) {
      // configure iff this stage has no parent or has a parent that is not a ResizeAsg stage
      configureTargets(stage)
      if (targetReferenceSupport.isDynamicallyBound(stage)) {
        injectBefore(stage, "determineTargetReferences", determineTargetReferenceStage, stage.context)
      }
      stage.initializationStage = true

      // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
      stage.status = ExecutionStatus.SUCCEEDED
      return []
    } else {
      def step1 = buildStep(stage, "resizeAsg", ResizeAsgTask)
      def step2 = buildStep(stage, "monitorAsg", MonitorKatoTask)
      def step3 = buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      def step4 = buildStep(stage, "waitForCapacityMatch", WaitForCapacityMatchTask)
      return [step1, step2, step3, step4]
    }
  }

  @CompileDynamic
  private void configureTargets(Stage stage) {
    def targetReferences = targetReferenceSupport.getTargetAsgReferences(stage)

    def descriptions = createResizeStageDescriptors(stage, targetReferences)

    if (descriptions.size()) {
      for (description in descriptions) {
        injectAfter(stage, "resizeAsg", this, description)
      }
    }

    targetReferences.each { targetReference ->
      def context = [
          credentials : stage.context.credentials,
          regions     : [targetReference.region]
      ]

      if (targetReferenceSupport.isDynamicallyBound(stage)) {
        def resizeContext = new HashMap(stage.context)
        resizeContext.regions = [targetReference.region]
        context.remove("asgName")
        context.target = stage.context.target
        injectAfter(stage, "resizeAsg", this, resizeContext)
      } else {
        context.asgName = targetReference.asg.name
      }

      injectBefore(stage, "resumeScalingProcesses", modifyScalingProcessStage, context + [
        action: "resume",
        processes: ["Launch", "Terminate"]
      ])
      injectAfter(stage, "suspendScalingProcesses", modifyScalingProcessStage, context + [
        action: "suspend"
      ])
    }
  }

  @CompileDynamic
  public List<Map<String, Object>> createResizeStageDescriptors(Stage stage, List<TargetReference> targetReferences) {
    def optionalConfig = stage.mapTo(OptionalConfiguration)
    Map<String, Map<String, Object>> descriptions = [:]

    for (TargetReference target : targetReferences) {
      def region = target.region
      def asg = target.asg

      def description = new HashMap(stage.context)

      if (!asg && targetReferenceSupport.isDynamicallyBound(stage)) {
        continue
      }

      if (descriptions.containsKey(asg.name)) {
        descriptions[asg.name as String].regions.add(region)
        continue
      }
      description.asgName = asg.name
      description.regions = [asg.region]

      def currentMin = Integer.parseInt(asg.asg.minSize.toString())
      def currentDesired = Integer.parseInt(asg.asg.desiredCapacity.toString())
      def currentMax = Integer.parseInt(asg.asg.maxSize.toString())

      int newMin, newDesired, newMax
      if (optionalConfig.scalePct) {
        def factor = optionalConfig.scalePct / 100
        def minDiff = Math.ceil(currentMin * factor)
        def desiredDiff = Math.ceil(currentDesired * factor)
        def maxDiff = Math.ceil(currentMax * factor)
        newMin = currentMin + minDiff
        newDesired = currentDesired + desiredDiff
        newMax = currentMax + maxDiff

        if (optionalConfig.action == ResizeAction.scale_down) {
          newMin = currentMin - minDiff
          newDesired = currentDesired - desiredDiff
          newMax = currentMax - maxDiff
        }
      } else if (optionalConfig.scaleNum) {
        newMin = currentMin + optionalConfig.scaleNum
        newDesired = currentDesired + optionalConfig.scaleNum
        newMax = currentMax + optionalConfig.scaleNum

        if (optionalConfig.action == ResizeAction.scale_down) {
          newMin = Math.max(currentMin - optionalConfig.scaleNum, 0)
          newDesired = Math.max(currentDesired - optionalConfig.scaleNum, 0)
          newMax = Math.max(currentMax - optionalConfig.scaleNum, 0)
        }
      }

      if (newMin && newDesired && newMax) {
        description.capacity = [min: newMin, desired: newDesired, max: newMax]
      } else {
        description.capacity = stage.context.capacity
      }

      descriptions[asg.name as String] = description
    }
    descriptions.values().flatten()
  }

  static enum ResizeAction {
    scale_up, scale_down
  }

  static class OptionalConfiguration {
    ResizeAction action
    Integer scalePct
    Integer scaleNum
  }
}
