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

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceLinearStageSupport
import com.netflix.spinnaker.orca.kato.tasks.DisableAsgTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Deprecated
class DisableAsgStage extends TargetReferenceLinearStageSupport {
  static final String PIPELINE_CONFIG_TYPE = "disableAsg"

  @Autowired
  Class<? extends Task> waitForAllInstancesDownOnDisableTaskType

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask)
      .withTask("disableAsg", DisableAsgTask)
      .withTask("monitorAsg", MonitorKatoTask)
      .withTask("waitForDownInstances", waitForAllInstancesDownOnDisableTaskType)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
  }
}
