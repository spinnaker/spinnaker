/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.proxmox;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport;
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.proxmox.StartProxmoxServerGroupTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import org.springframework.stereotype.Component;

/** Pipeline/orchestration stage type: {@code startProxmoxServerGroup}. */
@Component
public class StartProxmoxServerGroupStage extends TargetServerGroupLinearStageSupport {
  public static final String PIPELINE_CONFIG_TYPE = "startProxmoxServerGroup";

  @Override
  protected void taskGraphInternal(StageExecution stage, TaskNode.Builder builder) {
    builder
        .withTask("determineHealthProviders", DetermineHealthProvidersTask.class)
        .withTask("startServerGroup", StartProxmoxServerGroupTask.class)
        .withTask("monitorServerGroup", MonitorKatoTask.class)
        .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
  }
}
