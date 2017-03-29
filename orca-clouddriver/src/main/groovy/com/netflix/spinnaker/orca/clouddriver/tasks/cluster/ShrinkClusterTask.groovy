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
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import org.springframework.stereotype.Component

@Component
class ShrinkClusterTask extends AbstractClusterWideClouddriverTask {
  @Override
  protected TaskResult missingClusterResult(Stage stage, ClusterSelection clusterSelection) {
    def shrinkConfig = stage.mapTo(ShrinkConfig)
    if (shrinkConfig.shrinkToSize == 0) {
      return TaskResult.SUCCEEDED
    }
    return super.missingClusterResult(stage, clusterSelection)
  }

  @Override
  protected TaskResult emptyClusterResult(Stage stage, ClusterSelection clusterSelection, Map cluster) {
    def shrinkConfig = stage.mapTo(ShrinkConfig)
    if (shrinkConfig.shrinkToSize == 0) {
      return TaskResult.SUCCEEDED
    }
    return super.emptyClusterResult(stage, clusterSelection, cluster)
  }

  @Canonical
  static class ShrinkConfig {
    boolean allowDeleteActive = false
    int shrinkToSize = 1
    boolean retainLargerOverNewer = false
  }

  @Override
  String getClouddriverOperation() {
    "destroyServerGroup"
  }

  @Override
  List<TargetServerGroup> filterServerGroups(Stage stage, String account, Location location, List<TargetServerGroup> serverGroups) {
    List<Map> filteredGroups = super.filterServerGroups(stage, account, location, serverGroups)
    if (!filteredGroups) {
      return []
    }

    def shrinkConfig = stage.mapTo(ShrinkConfig)
    filteredGroups = filterActiveGroups(shrinkConfig.allowDeleteActive, filteredGroups)
    def comparators = []
    int dropCount = shrinkConfig.shrinkToSize - (serverGroups.size() - filteredGroups.size())
    if (shrinkConfig.allowDeleteActive) {
      comparators << new IsActive()
    }
    if (shrinkConfig.retainLargerOverNewer) {
      comparators << new InstanceCount()
    }
    comparators << new CreatedTime()

    //result will be sorted in priority order to retain
    def prioritized = filteredGroups.sort(false, new CompositeComparator(comparators))

    return prioritized.drop(dropCount)
  }
}
