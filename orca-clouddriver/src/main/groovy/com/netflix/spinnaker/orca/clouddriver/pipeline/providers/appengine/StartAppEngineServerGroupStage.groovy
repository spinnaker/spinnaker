/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.appengine

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine.StartAppEngineServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine.WaitForAppEngineServerGroupStartTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

@Component
@CompileStatic
class StartAppEngineServerGroupStage extends TargetServerGroupLinearStageSupport {
  public static final String PIPELINE_CONFIG_TYPE = "startServerGroup"

  @Override
  protected void taskGraphInternal(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask)
      .withTask("startServerGroup", StartAppEngineServerGroupTask)
      .withTask("monitorServerGroup", MonitorKatoTask)
      .withTask("waitForServerGroupStart", WaitForAppEngineServerGroupStartTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
  }
}
