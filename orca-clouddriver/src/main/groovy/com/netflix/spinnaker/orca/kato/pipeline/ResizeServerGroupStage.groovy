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

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.kato.tasks.ResizeServerGroupTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForCapacityMatchTask
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
class ResizeServerGroupStage extends TargetServerGroupLinearStageSupport {

  public static final String TYPE = "resizeServerGroup"

  @Autowired
  ModifyAwsScalingProcessStage modifyAwsScalingProcessStage

  ResizeServerGroupStage() {
    super(TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    composeTargets(stage)

    return [
      buildStep(stage, "resizeServerGroup", ResizeServerGroupTask),
      buildStep(stage, "monitorServerGroup", MonitorKatoTask),
      buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
      buildStep(stage, "waitForCapacityMatch", WaitForCapacityMatchTask),
    ]
  }

  @Override
  protected List<Map<String, Object>> buildStaticTargetDescriptions(Stage stage, List<TargetServerGroup> targets) {
    ResizeSupport.createResizeDescriptors(stage, targets)
  }


  @Override
  protected List<Injectable> preStatic(Map descriptor) {
    if (descriptor.cloudProvider != 'aws') {
      return []
    }
    [new Injectable(
      name: "resumeScalingProcesses",
      stage: modifyAwsScalingProcessStage,
      context: [
        asgName      : descriptor.asgName,
        cloudProvider: descriptor.cloudProvider,
        credentials  : descriptor.credentials,
        regions      : descriptor.regions,
        action       : "resume",
        processes    : ["Launch", "Terminate"]
      ]
    )]
  }

  @Override
  protected List<Injectable> postStatic(Map descriptor) {
    if (descriptor.cloudProvider != 'aws') {
      return []
    }
    [new Injectable(
      name: "suspendScalingProcesses",
      stage: modifyAwsScalingProcessStage,
      context: [
        asgName      : descriptor.asgName,
        cloudProvider: descriptor.cloudProvider,
        credentials  : descriptor.credentials,
        regions      : descriptor.regions,
        action       : "suspend"
      ]
    )]
  }

  @Override
  protected List<Injectable> preDynamic(Map context) {
    if (context.cloudProvider != 'aws') {
      return []
    }
    context.remove("asgName")
    [new Injectable(
      name: "resumeScalingProcesses",
      stage: modifyAwsScalingProcessStage,
      context: context + [
        action   : "resume",
        processes: ["Launch", "Terminate"]
      ]
    )]
  }

  @Override
  protected List<Injectable> postDynamic(Map context) {
    if (context.cloudProvider != 'aws') {
      return []
    }
    context.remove("asgName")
    [new Injectable(
      name: "suspendScalingProcesses",
      stage: modifyAwsScalingProcessStage,
      context: context + [
        action: "suspend",
      ],
    )]
  }
}
