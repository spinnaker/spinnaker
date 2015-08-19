/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.gce

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.pipeline.DetermineTargetReferenceStage
import com.netflix.spinnaker.orca.kato.pipeline.ModifyScalingProcessStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.kato.tasks.gce.GoogleServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.gce.ResizeGoogleReplicaPoolTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Slf4j
class ResizeGoogleReplicaPoolStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "resizeAsg_gce"

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  ResizeSupport resizeSupport

  @Autowired
  ModifyScalingProcessStage modifyScalingProcessStage

  @Autowired
  DetermineTargetReferenceStage determineTargetReferenceStage

  ResizeGoogleReplicaPoolStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  /**
   * This method can be invoked in two ways: via Pipeline or Orchestration (from the UI).
   *
   * For pipelines: the replica pool (aka managed instance groups) being resized is usually found dynamically, which is
   * the job of the {@link DetermineTargetReferenceStage} object, which is injected before this one. A duplicate
   * {@code ResizeGoogleReplicaPoolStage} is then injected after this stage that can read from the injected
   * {@code DetermineTargetReferenceStage}'s output.
   *
   * @param stage
   * @return
   */
  @Override
  public List<Step> buildSteps(Stage stage) {
    if (isOriginalResizePipelineStage(stage)) {
      return expandOriginalResizePipeline(stage)
    }

    def step1 = buildStep(stage, "resizeServerGroup", ResizeGoogleReplicaPoolTask)
    def step2 = buildStep(stage, "monitorServerGroup", MonitorKatoTask)
    def step3 = buildStep(stage, "forceCacheRefresh", GoogleServerGroupCacheForceRefreshTask)
    def step4 = buildStep(stage, "waitForCapacityMatch", WaitForCapacityMatchTask)
    [step1, step2, step3, step4]
  }

  /**
   * @return true iff this stage has no parent or has a parent that is not a ResizeAsg stage
   */
  private boolean isOriginalResizePipelineStage(Stage stage) {
    return !stage.parentStageId || stage.execution.stages.find { it.id == stage.parentStageId}.type != stage.type
  }

  private List<Step> expandOriginalResizePipeline(Stage stage) {
    configureTargets(stage)
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      injectBefore(stage, "determineTargetReferences", determineTargetReferenceStage, stage.context)
    }
    stage.initializationStage = true

    // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
    stage.status = ExecutionStatus.SUCCEEDED
    return []
  }

  private void configureTargets(Stage stage) {
    // Get the Target references just for the count.
    def targetReferences = targetReferenceSupport.getTargetAsgReferences(stage)
    if (targetReferences) {
      for (ref in targetReferences) {
        injectAfter(stage, "resizeServerGroup", this, stage.context)
      }
    }
  }
}
