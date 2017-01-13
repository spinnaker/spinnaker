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

import java.util.concurrent.ConcurrentHashMap
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.tasks.quip.ResolveQuipVersionTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Client
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage

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

  private static INSTANCE_VERSION_SLEEP = 10000

  @Override
  def <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder.withTask("resolveQuipVersion", ResolveQuipVersionTask)
  }

  @Override
  def <T extends Execution<T>> List<Stage<T>> aroundStages(Stage<T> stage) {
    def stages = []

    def instances = getInstancesForCluster(stage)
    if (instances.size() == 0) {
      // skip since nothing to do
    } else if (stage.context.rollingPatch) {
      // rolling means instances in the asg will be updated sequentially
      instances.each { key, value ->
        def instance = [:]
        instance.put(key, value)
        def nextStageContext = [:]
        nextStageContext.putAll(stage.context)
        nextStageContext << [instances: instance]
        nextStageContext.put("instanceIds", [key]) // for WaitForDown/UpInstancesTask

        stages << newStage(
          stage.execution,
          bulkQuickPatchStage.type,
          "bulkQuickPatchStage",
          nextStageContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }
    } else { // quickpatch all instances in the asg at once
      def nextStageContext = [:]
      nextStageContext.putAll(stage.context)
      nextStageContext << [instances: instances]
      nextStageContext.put("instanceIds", instances.collect { key, value -> key })
      // for WaitForDown/UpInstancesTask

      stages << newStage(
        stage.execution,
        bulkQuickPatchStage.type,
        "bulkQuickPatchStage",
        nextStageContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    stage.initializationStage = true
    // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
    stage.status = ExecutionStatus.SUCCEEDED

    return stages
  }

  Map getInstancesForCluster(Stage stage) {
    ConcurrentHashMap instances = new ConcurrentHashMap(oortHelper.getInstancesForCluster(stage.context, null, true, false))
    return instances
  }

}
