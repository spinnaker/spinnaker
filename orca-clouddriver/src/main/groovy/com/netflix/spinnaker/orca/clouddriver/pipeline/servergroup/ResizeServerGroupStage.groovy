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
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
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

  @Override
  protected void taskGraphInternal(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask)
      .withTask("resizeServerGroup", ResizeServerGroupTask)
      .withTask("monitorServerGroup", MonitorKatoTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      .withTask("waitForCapacityMatch", WaitForCapacityMatchTask)
  }

  @Override
  protected void preStatic(Map<String, Object> descriptor, StageGraphBuilder graph) {
    if (descriptor.cloudProvider == "aws") {
      graph.add {
        it.name = "resumeScalingProcesses"
        it.type = ModifyAwsScalingProcessStage.TYPE
        it.context = [
          serverGroupName: getServerGroupName(descriptor),
          cloudProvider  : descriptor.cloudProvider,
          credentials    : descriptor.credentials,
          region         : descriptor.region,
          action         : "resume",
          processes      : ["Launch", "Terminate"]
        ]
      }
    }
  }

  @Override
  protected void postStatic(Map<String, Object> descriptor, StageGraphBuilder graph) {
    if (descriptor.cloudProvider == "aws") {
      graph.add {
        it.name = "suspendScalingProcesses"
        it.type = ModifyAwsScalingProcessStage.TYPE
        it.context = [
          serverGroupName: getServerGroupName(descriptor),
          cloudProvider  : descriptor.cloudProvider,
          credentials    : descriptor.credentials,
          region         : descriptor.region,
          action         : "suspend"
        ]
      }
    }
  }

  @Override
  protected void preDynamic(Map<String, Object> context, StageGraphBuilder graph) {
    if (context.cloudProvider == "aws") {
      context = removeServerGroupName(context)
      graph.add {
        it.name = "resumeScalingProcesses"
        it.type = ModifyAwsScalingProcessStage.TYPE
        it.context.putAll(context)
        it.context["action"] = "resume"
        it.context["processes"] = ["Launch", "Terminate"]
      }
    }
  }

  @Override
  protected void postDynamic(Map<String, Object> context, StageGraphBuilder graph) {
    if (context.cloudProvider == "aws") {
      context = removeServerGroupName(context)
      graph.add {
        it.name = "suspendScalingProcesses"
        it.type = ModifyAwsScalingProcessStage.TYPE
        it.context.putAll(context)
        it.context["action"] = "suspend"
      }
    }
  }
}
