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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster

import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterShrinkTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ShrinkClusterStage extends AbstractClusterWideClouddriverOperationStage {
  public static final String PIPELINE_CONFIG_TYPE = "shrinkCluster"

  @Autowired
  DisableClusterStage disableClusterStage

  ShrinkClusterStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask() {
    ShrinkClusterTask
  }

  @Override
  Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask() {
    WaitForClusterShrinkTask
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    if (stage.context.allowDeleteActive == true) {
      //TODO(cfieber) Remvove the stage.context.cloudProvider check once proper discovery has been added to titus
      if (!stage.context.cloudProvider || stage.context.cloudProvider != 'titan') {
        injectBefore(stage, "disableCluster", disableClusterStage, stage.context + [
          remainingEnabledServerGroups  : stage.context.shrinkToSize,
          preferLargerOverNewer         : stage.context.retainLargerOverNewer,
          continueIfClusterNotFound     : stage.context.shrinkToSize == 0,
          interestingHealthProviderNames: stage.context.interestingHealthProviderNames
        ])
      }
    }
    return super.buildSteps(stage)
  }
}
