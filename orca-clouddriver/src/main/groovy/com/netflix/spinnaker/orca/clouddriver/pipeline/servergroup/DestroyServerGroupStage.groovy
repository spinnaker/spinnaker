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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.ForceCacheRefreshAware
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DestroyServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForDestroyedServerGroupTask
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DestroyServerGroupStage extends TargetServerGroupLinearStageSupport implements ForceCacheRefreshAware {
  private static final Logger log = LoggerFactory.getLogger(DestroyServerGroupStage.class)
  
  static final String PIPELINE_CONFIG_TYPE = "destroyServerGroup"

  private final DynamicConfigService dynamicConfigService

  @Autowired
  DestroyServerGroupStage(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService
  }

  private static void addDisableStage(Map<String, Object> context, StageGraphBuilderImpl graph) {
    boolean skipDisable = (boolean)context.getOrDefault("skipDisableBeforeDestroy", false)

    if (!skipDisable) {
      // conditional opt-out for server groups where an explicit disable is unnecessary 
      // (ie. they do not register in service discovery or a load balancer)
      graph.add {
        it.name = "disableServerGroup"
        it.type = getType(DisableServerGroupStage)
        it.context.putAll(context)
      }
    } else {
      log.info("DisableServerGroupStage has been skipped (skipDisableBeforeDestroy: true)")
    }
  }

  @Override
  protected void preStatic(Map<String, Object> context, StageGraphBuilderImpl graph) {
    addDisableStage(context, graph)
  }

  @Override
  protected void preDynamic(Map<String, Object> context, StageGraphBuilderImpl graph) {
    addDisableStage(context, graph)
  }

  @Override
  protected void taskGraphInternal(StageExecution stage, TaskNode.Builder builder) {
    builder
      .withTask("destroyServerGroup", DestroyServerGroupTask)
      .withTask("monitorServerGroup", MonitorKatoTask)
      .withTask("waitForDestroyedServerGroup", WaitForDestroyedServerGroupTask)
  }
}
