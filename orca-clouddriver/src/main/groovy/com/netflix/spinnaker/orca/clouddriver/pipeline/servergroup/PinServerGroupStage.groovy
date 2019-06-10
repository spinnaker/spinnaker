/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ResizeServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

/**
 * PinServerGroupStage updates the capacity (min/max/desired) on a server group but does not wait for capacity match,
 * unlike ResizeServerGroup.
 * Furthermore, it doesn't enabled/disable scaling processes. It simply sets the min/max/desired sizes on an ASG.
 *
 * The intent of the stage is to pin to prevent the ASG from scaling down, or, less usefully, up. As such,
 * the expectation is that the right number of instances are already up and no need to wait for the capacity to match.
 * That's why we can also ignore reenabling/disabling the scaling processes during this operation.
 * This stage is used with rolling red black.
 */
@Component
@Slf4j
class PinServerGroupStage extends TargetServerGroupLinearStageSupport {
  public static final String TYPE = getType(PinServerGroupStage)

  @Override
  protected void taskGraphInternal(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("resizeServerGroup", ResizeServerGroupTask)
      .withTask("monitorServerGroup", MonitorKatoTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
  }
}
