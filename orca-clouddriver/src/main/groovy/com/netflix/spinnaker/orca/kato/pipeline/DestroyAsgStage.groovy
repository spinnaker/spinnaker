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

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DestroyServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForDestroyedServerGroupTask
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceLinearStageSupport
import com.netflix.spinnaker.orca.kato.tasks.DestroyAwsServerGroupTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

/**
 * @deprecated - Use the super class {@link DestroyServerGroupStage}
 * This class only exists so that existing persisted pipeline configs that have 'destroyAsg' configured, do not break
 */
@Component
@CompileStatic
@Deprecated
class DestroyAsgStage extends TargetReferenceLinearStageSupport {
  static final String DESTROY_ASG_DESCRIPTIONS_KEY = "destroyAsgDescriptions"

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("destroyServerGroup", DestroyAwsServerGroupTask)
      .withTask("monitorServerGroup", MonitorKatoTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      .withTask("waitForDestroyedServerGroup", WaitForDestroyedServerGroupTask)
  }
}
