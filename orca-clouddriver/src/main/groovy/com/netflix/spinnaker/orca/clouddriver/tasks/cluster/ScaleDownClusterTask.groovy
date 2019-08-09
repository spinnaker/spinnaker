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

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.AbstractClusterWideClouddriverOperationStage.ClusterSelection
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
class ScaleDownClusterTask extends AbstractClusterWideClouddriverTask {
  @Override
  String getClouddriverOperation() {
    'resizeServerGroup'
  }

  @Canonical
  private static class ScaleDownClusterConfig {
    int remainingFullSizeServerGroups = 1
    boolean preferLargerOverNewer = false
    boolean allowScaleDownActive = false
  }

  @Override
  protected Map buildOperationPayload(Stage stage, TargetServerGroup serverGroup) {
    ClusterSelection clusterSelection = stage.mapTo(ClusterSelection)
    Map resizeOp = [capacity: [min: 0, max: 0, desired: 0]]
    if (clusterSelection.cloudProvider == 'gce' && serverGroup.getAutoscalingPolicy()) {
      resizeOp.autoscalingMode = 'OFF'

      // Avoid overriding persisted autoscaling policy metadata to 0/0/0 and mode = OFF.
      // This ensures the server group will be scaled properly with the previously persisted
      // autoscaling policy if re-enabled.
      resizeOp.writeMetadata = false
    }

    return super.buildOperationPayload(stage, serverGroup) + resizeOp
  }

  @Override
  protected List<Map> buildOperationPayloads(Stage stage, TargetServerGroup serverGroup) {
    ClusterSelection clusterSelection = stage.mapTo(ClusterSelection)
    List<Map> ops = []
    if (clusterSelection.cloudProvider == 'aws') {
      ops << [resumeAsgProcessesDescription: serverGroup.toClouddriverOperationPayload(clusterSelection.credentials) + [
        processes: ['Terminate']
      ]]
    }

    ops + super.buildOperationPayloads(stage, serverGroup)
  }

  @Override
  List<TargetServerGroup> filterServerGroups(Stage stage, String account, Location location, List<TargetServerGroup> serverGroups) {
    List<Map> filteredGroups = super.filterServerGroups(stage, account, location, serverGroups)
    def config = stage.mapTo(ScaleDownClusterConfig)
    filteredGroups = filterActiveGroups(config.allowScaleDownActive, filteredGroups)

    def comparators = []
    int dropCount = Math.max(0, config.remainingFullSizeServerGroups - (serverGroups.size() - filteredGroups.size()))
    if (config.allowScaleDownActive) {
      comparators << new IsActive()
    }
    if (config.preferLargerOverNewer) {
      comparators << new InstanceCount()
    }
    comparators << new CreatedTime()

    //result will be sorted in priority order to retain
    def prioritized = filteredGroups.sort(false, new CompositeComparator(comparators))

    log.info("$stage.execution.id: Retained $prioritized from $serverGroups, will drop $dropCount")

    return prioritized.drop(dropCount)
  }

}
