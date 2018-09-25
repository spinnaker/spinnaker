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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.AbstractClusterWideClouddriverOperationStage.ClusterSelection
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class WaitForClusterShrinkTask extends AbstractWaitForClusterWideClouddriverTask {
  @Override
  protected TaskResult missingClusterResult(Stage stage, ClusterSelection clusterSelection) {
    TaskResult.SUCCEEDED
  }

  @Override
  protected TaskResult emptyClusterResult(Stage stage, ClusterSelection clusterSelection, Map cluster) {
    TaskResult.SUCCEEDED
  }

  @Override
  boolean isServerGroupOperationInProgress(Stage stage, List<Map> interestingHealthProviderNames, Optional<TargetServerGroup> currentServerGroup) {
    currentServerGroup.isPresent()
  }
}
