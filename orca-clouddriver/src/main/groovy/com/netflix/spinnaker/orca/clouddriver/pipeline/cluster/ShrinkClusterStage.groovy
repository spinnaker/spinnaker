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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ShrinkClusterStage extends AbstractClusterWideClouddriverOperationStage {
  @Autowired
  DisableClusterStage disableClusterStage

  @Override
  Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask() {
    ShrinkClusterTask
  }

  @Override
  Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask() {
    WaitForClusterShrinkTask
  }

  @Override
  def <T extends Execution<T>> List<Stage<T>> aroundStages(Stage<T> stage) {
    if (stage.context.allowDeleteActive == true) {
      def context = stage.context + [
        remainingEnabledServerGroups  : stage.context.shrinkToSize,
        preferLargerOverNewer         : stage.context.retainLargerOverNewer,
        continueIfClusterNotFound     : stage.context.shrinkToSize == 0,
      ]

      // We don't want the key propagated if interestingHealthProviderNames isn't defined, since this prevents
      // health providers from the stage's 'determineHealthProviders' task to be added to the context.
      if (stage.context.interestingHealthProviderNames != null) {
        context.interestingHealthProviderNames = stage.context.interestingHealthProviderNames
      }

      return [
        newStage(
          stage.execution, disableClusterStage.type, "disableCluster", context, stage, SyntheticStageOwner.STAGE_BEFORE
        )
      ]
    }
    return []
  }
}
