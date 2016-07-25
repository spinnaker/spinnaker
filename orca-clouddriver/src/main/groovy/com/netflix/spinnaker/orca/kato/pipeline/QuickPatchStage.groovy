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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.tasks.quip.ResolveQuipVersionTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.ConcurrentHashMap

/**
 * Wrapper stage over BuilkQuickPatchStage.  We do this so we can reuse the same steps whether or not we are doing
 * a rolling quick patch.  The difference is that the rolling version will only update one instance at a time while
 * the non-rolling version will act on all instances at once.  This is done by controlling the instances we
 * send to BuilkQuickPatchStage.
 */
@Slf4j
@Component
class QuickPatchStage extends LinearStage {

  @Autowired
  BulkQuickPatchStage bulkQuickPatchStage

  @Autowired
  OortHelper oortHelper

  public static final String PIPELINE_CONFIG_TYPE = "quickPatch"

  QuickPatchStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    def instances = getInstancesForCluster(stage)
    List<Step> steps = []
    if (instances) {
      steps << buildStep(stage, 'foo', ResolveQuipVersionTask)
      if (stage.context.rollingPatch) {
        instances.each { key, value ->
          def instance = [:]
          instance.put(key, value)
          def nextStageContext = [:]
          nextStageContext.putAll(stage.context)
          nextStageContext << [instances: instance]
          injectAfter(stage, "bulkQuickPatchStage", bulkQuickPatchStage, nextStageContext)
        }
      } else { // quickpatch all instances in the asg at once
        def nextStageContext = [:]
        nextStageContext.putAll(stage.context)
        nextStageContext << [instances: instances]
        injectAfter(stage, "bulkQuickPatchStage", bulkQuickPatchStage, nextStageContext)
      }
    } else {
      stage.initializationStage = true
      // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
      stage.status = ExecutionStatus.SUCCEEDED
    }
    return steps
  }

  Map getInstancesForCluster(Stage stage) {
    ConcurrentHashMap instances = new ConcurrentHashMap(oortHelper.getInstancesForCluster(stage.context, null, true, false))
    return instances
  }

}
