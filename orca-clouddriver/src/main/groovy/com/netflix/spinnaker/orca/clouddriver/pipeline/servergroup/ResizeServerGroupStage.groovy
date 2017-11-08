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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ModifyAwsScalingProcessStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ResizeServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
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
  public static final String TYPE = getType(ResizeServerGroupStage)

  @Autowired
  ModifyAwsScalingProcessStage modifyAwsScalingProcessStage

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask)
      .withTask("resizeServerGroup", ResizeServerGroupTask)
      .withTask("monitorServerGroup", MonitorKatoTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      .withTask("waitForCapacityMatch", WaitForCapacityMatchTask)
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
        serverGroupName: descriptor.asgName,
        cloudProvider  : descriptor.cloudProvider,
        credentials    : descriptor.credentials,
        region         : descriptor.region,
        action         : "resume",
        processes      : ["Launch", "Terminate"]
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
        serverGroupName: descriptor.asgName,
        cloudProvider  : descriptor.cloudProvider,
        credentials    : descriptor.credentials,
        region         : descriptor.region,
        action         : "suspend"
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
