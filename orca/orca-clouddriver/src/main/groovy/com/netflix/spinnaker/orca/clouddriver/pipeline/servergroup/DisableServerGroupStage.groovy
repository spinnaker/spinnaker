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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.ForceCacheRefreshAware
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DisableServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForDisabledServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForRequiredInstancesDownTask
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.cloudrun.WaitForCloudrunInstancesDownTask
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DisableServerGroupStage extends TargetServerGroupLinearStageSupport implements ForceCacheRefreshAware, CloudProviderAware {
  static final String PIPELINE_CONFIG_TYPE = "disableServerGroup"

  private final Environment environment

  @Autowired
  DisableServerGroupStage(Environment environment) {
    this.environment = environment
  }

  @Override
  protected void taskGraphInternal(StageExecution stage, TaskNode.Builder builder) {

    String cloudProvider = getCloudProvider(stage);
    builder
        .withTask("determineHealthProviders", DetermineHealthProvidersTask)
        .withTask("disableServerGroup", DisableServerGroupTask)
        .withTask("monitorServerGroup", MonitorKatoTask)
    if ("cloudrun".equals(cloudProvider)) {
      builder.withTask("waitForDownInstances", WaitForCloudrunInstancesDownTask)
    } else {
      builder.withTask("waitForDownInstances", WaitForRequiredInstancesDownTask)
    }
    builder.withTask("waitForServerGroupDisabled", WaitForDisabledServerGroupTask)
    if (isForceCacheRefreshEnabled(environment)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    }
  }
}
