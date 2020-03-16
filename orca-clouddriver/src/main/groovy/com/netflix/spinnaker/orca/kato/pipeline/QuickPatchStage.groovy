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

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory

import javax.annotation.Nonnull
import java.util.concurrent.ConcurrentHashMap
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.tasks.quip.ResolveQuipVersionTask
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Client

/**
 * Wrapper stage over BuilkQuickPatchStage.  We do this so we can reuse the same steps whether or not we are doing
 * a rolling quick patch.  The difference is that the rolling version will only update one instance at a time while
 * the non-rolling version will act on all instances at once.  This is done by controlling the instances we
 * send to BuilkQuickPatchStage.
 */
@Slf4j
@Component
@CompileStatic
class QuickPatchStage implements StageDefinitionBuilder {

  @Autowired
  BulkQuickPatchStage bulkQuickPatchStage

  @Autowired
  OortHelper oortHelper

  @Autowired
  Client retrofitClient

  public static final String PIPELINE_CONFIG_TYPE = "quickPatch"

  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("resolveQuipVersion", ResolveQuipVersionTask)
  }

  @Override
  void beforeStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
    // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
    parent.status = ExecutionStatus.SUCCEEDED
  }

  @Override
  void afterStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {
    List<StageExecution> stages = new ArrayList<>()
    Map<String, Object> nextStageContext = new HashMap<>()

    def instances = getInstancesForCluster(stage)
    if (instances.size() == 0) {
      // skip since nothing to do
    } else if (stage.context.rollingPatch) {
      // rolling means instances in the asg will be updated sequentially
      instances.each { key, value ->
        def instance = [:]
        instance.put(key, value)
        nextStageContext.putAll(stage.context)
        nextStageContext.put("instances", instance)
        nextStageContext.put("instanceIds", [key]) // for WaitForDown/UpInstancesTask

        stages << StageExecutionFactory.newStage(
          stage.execution,
          bulkQuickPatchStage.type,
          "bulkQuickPatchStage",
          nextStageContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }
    } else { // quickpatch all instances in the asg at once
      nextStageContext.putAll(stage.context)
      nextStageContext.put("instances", instances)
      nextStageContext.put("instanceIds", instances.collect { key, value -> key })
      // for WaitForDown/UpInstancesTask

      stages << StageExecutionFactory.newStage(
        stage.execution,
        bulkQuickPatchStage.type,
        "bulkQuickPatchStage",
        nextStageContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    stages.forEach({ graph.append(it) })
  }

  Map getInstancesForCluster(StageExecution stage) {
    ConcurrentHashMap instances = new ConcurrentHashMap(oortHelper.getInstancesForCluster(stage.context, null, true, false))
    return instances
  }

}
