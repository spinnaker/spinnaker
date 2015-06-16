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

package com.netflix.spinnaker.orca.oort.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class FindAmiFromClusterTask implements Task {

  static enum SelectionStrategy {
    /**
     * Choose the server group with the most instances, falling back to newest in the case of a tie
     */
    LARGEST({ List<Map<String, Object>> sgs ->
      sgs.sort { lhs, rhs ->
        rhs.instances.size() <=> lhs.instances.size() ?:
          rhs.asg.createdTime <=> lhs.asg.createdTime
      }.getAt(0)
    }),

    /**
     * Choose the newest ServerGroup by createdTime
     */
    NEWEST({ List<Map<String, Object>> sgs ->
      sgs.sort { lhs, rhs ->
        rhs.asg.createdTime <=> lhs.asg.createdTime
      }.getAt(0)
    }),

    /**
     * Choose the oldest ServerGroup by createdTime
     */
    OLDEST({ List<Map<String, Object>> sgs ->
      sgs.sort { lhs, rhs ->
        lhs.asg.createdTime <=> rhs.asg.createdTime
      }.getAt(0)
    }),

    /**
     * Fail if there is more than one server group to choose from
     */
    FAIL({ List<Map<String, Object>> sgs ->
      if (sgs.size() > 1) {
        throw new IllegalStateException("Multiple possible server groups present in ${sgs.getAt(0).region}: ${sgs*.name}")
      }

      return sgs.getAt(0)
    })

    private final Closure<Map<String, Object>> strategy

    private SelectionStrategy(Closure<Map<String, Object>> strategy) {
      this.strategy = strategy
    }

    Map<String, Object> apply(List<Map<String, Object>> serverGroups) {
      strategy.call(serverGroups)
    }
  }

  @Autowired OortService oortService
  @Autowired ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    String app = stage.context.cluster.split('-')[0]
    String account = stage.context.account
    String cluster = stage.context.cluster
    Set<String> requiredRegions = new HashSet<>(stage.context.regions?.asType(List) ?: [])

    boolean onlyEnabled = stage.context.onlyEnabled == null ? true : (Boolean.valueOf(stage.context.onlyEnabled))

    SelectionStrategy clusterSelectionStrategy = SelectionStrategy.valueOf(stage.context.selectionStrategy?.toString() ?: "NEWEST")

    TypeReference<Map<String, Object>> jsonType = new TypeReference<Map<String, Object>>() {}
    Map<String, Object> clusterData = objectMapper.readValue(oortService.getCluster(app, account, cluster, "aws").body.in(), jsonType)


    List<Map<String, Object>> sgs = clusterData.serverGroups

    if (onlyEnabled) {
      sgs = sgs.findAll { sg ->
        sg.asg.suspendedProcesses.every { proc ->
          proc.processName != 'AddToLoadBalancer'
        }
      }
    }

    Map<String, Map<String, Object>> byRegion = sgs
      .groupBy { it.region }
      .collectEntries { r, g -> [(r): clusterSelectionStrategy.apply(g)] }

    List<Map> deploymentDetails = byRegion.findResults { region, serverGroup ->
      if (serverGroup == null) {
        return null
      }

      if (requiredRegions && !requiredRegions.contains(region)) {
        return null
      }

      [region: region, ami: serverGroup.image.imageId, imageName: serverGroup.image.name, sourceServerGroup: serverGroup.name] + (Map) serverGroup.image + (Map) serverGroup.buildInfo
    }

    if (requiredRegions && deploymentDetails.size() != requiredRegions.size()) {
      throw new IllegalStateException("Unable to find AMI for all regions")
    }


    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      amiDetails: deploymentDetails
    ], [
      deploymentDetails: deploymentDetails
    ])
  }
}
