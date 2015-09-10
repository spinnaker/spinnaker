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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.kato.tasks.ResizeServerGroupTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * ResizeServerGroupStage intercepts requests to resize a server group and injects various pre- and
 * post-stage requirements before actually delegating the resize operation to the {@code Delegate} static
 * inner class.
 */
@Component
@Slf4j
class ResizeServerGroupStage extends LinearStage {

  public static final String TYPE = "resizeServerGroup"

  @Autowired
  DetermineTargetServerGroupStage determineTargetServerGroupStage

  @Autowired
  ModifyScalingProcessStage modifyScalingProcessStage

  @Autowired
  Delegate delegate

  @Autowired
  TargetServerGroupResolver targetServerGroupResolver

  ResizeServerGroupStage() {
    super(TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    // Always resolve the target list of server groups during pipeline configuration, because the last thing you want is
    // for a pipeline to fail towards the end because of a silly mistake that could have been caught (like your 'oldest'
    // target didn't have at least 2 server groups).
    def tsgs = targetServerGroupResolver.resolve(TargetServerGroup.Params.fromStage(stage))
    TargetServerGroup.isDynamicallyBound(stage) ? injectDynamicSteps(stage, tsgs) : injectStaticSteps(stage, tsgs)

    // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
    stage.status = ExecutionStatus.SUCCEEDED
    return []
  }

  /**
   * Used by Pipelines that dynamically target a server group, such as "current" or "oldest."
   * A synthetic target resolver stage ("DetermineTargetReferencesStage") is injected prior to actually doing the resize
   * stage ("Delegate").
   */
  def injectDynamicSteps(Stage stage, List<TargetServerGroup> tsgs) {
    for (tsg in tsgs) {
      def context = stage.context + [regions: [tsg.location]]
      injectAfter(stage, Delegate.TYPE, delegate, context)

      // TODO(ttomsu): Remove this provider-specific piece somewhere else.
      if (stage.context.cloudProvider == 'aws') {
        def scalingContext = new HashMap(context)
        scalingContext.remove("asgName")
        addScalingProcesses(stage, scalingContext)
      }
    }

    // For silly reasons, this must be added after the scaling processes to get the execution order right.
    injectBefore(stage, "determineTargetServerGroupStage", determineTargetServerGroupStage, stage.context)
  }

  /**
   * Used by Pipelines that statically target a server group (deprecated "feature") AND by Orchestrations driven by the
   * UI that targets a specific server group. The server group is specified in the UI or resolved at pipeline setup
   * time.
   */
  def injectStaticSteps(Stage stage, List<TargetServerGroup> tsgs) {
    def descriptions = ResizeSupport.createResizeDescriptors(stage, tsgs)

    for (description in descriptions) {
      // Put the operation description right into the Delegate's context, so the task can just it directly.
      injectAfter(stage, Delegate.TYPE, delegate, description)
    }
    // Each AWS region's scaling policies must be disabled before resizing can occur.
    if (stage.context.cloudProvider == "aws") {
      for (tsg in tsgs) {
        addScalingProcesses(stage, [
          credentials: stage.context.credentials,
          regions    : [tsg.location],
          asgName    : tsg.serverGroup.name,
        ])
      }
    }
  }

  void addScalingProcesses(Stage stage, Map<String, Object> baseContext) {
    // TODO(ttomsu): Get ModifyScalingProcessStage working.
//    injectBefore(stage, "resumeScalingProcesses", modifyScalingProcessStage, baseContext + [
//      action   : "resume",
//      processes: ["Launch", "Terminate"]
//    ])
//    injectAfter(stage, "suspendScalingProcesses", modifyScalingProcessStage, baseContext + [
//      action: "suspend"
//    ])
  }

  /**
   * This class contains the logic for actually resizing a server group.
   */
  @Component
  @Slf4j
  static class Delegate extends LinearStage {

    public static final String TYPE = "resizeServerGroup_delegate"

    Delegate() {
      super(TYPE)
    }

    @Override
    List<Step> buildSteps(Stage stage) {
      [
        buildStep(stage, "resizeServerGroup", ResizeServerGroupTask),
        buildStep(stage, "monitorServerGroup", MonitorKatoTask),
        buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
        buildStep(stage, "waitForCapacityMatch", WaitForCapacityMatchTask),
      ]
    }
  }
}
