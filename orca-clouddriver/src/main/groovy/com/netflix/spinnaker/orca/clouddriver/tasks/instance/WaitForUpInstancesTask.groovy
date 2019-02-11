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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper.HealthCountSnapshot
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
class WaitForUpInstancesTask extends AbstractWaitingForInstancesTask {

  static final int MIN_ZERO_INSTANCE_RETRY_COUNT = 12

  // We do not want to fail before the server group has been created.
  boolean waitForUpServerGroup() {
    return true
  }

  @Override
  Map getAdditionalRunningStageContext(Stage stage, Map serverGroup) {
    def additionalRunningStageContext = [
        targetDesiredSize: calculateTargetDesiredSize(stage, serverGroup),
        lastCapacityCheck: getHealthCountSnapshot(stage, serverGroup)
    ]

    if (!stage.context.capacitySnapshot) {
      def initialTargetCapacity = getServerGroupCapacity(stage, serverGroup)
      additionalRunningStageContext.capacitySnapshot = [
          minSize        : initialTargetCapacity.min,
          desiredCapacity: initialTargetCapacity.desired,
          maxSize        : initialTargetCapacity.max
      ]
    }

    return additionalRunningStageContext
  }

  static boolean allInstancesMatch(Stage stage,
                                   Map serverGroup,
                                   List<Map> instances,
                                   Collection<String> interestingHealthProviderNames) {
    if (!(serverGroup?.capacity)) {
      return false
    }
    int targetDesiredSize = calculateTargetDesiredSize(stage, serverGroup)

    if (targetDesiredSize == 0 && stage.context.capacitySnapshot) {
      // if we've seen a non-zero value before, but we are seeing a target size of zero now, assume
      // it's a transient issue with edda unless we see it repeatedly
      Map snapshot = stage.context.capacitySnapshot as Map
      Integer snapshotDesiredCapacity = snapshot.desiredCapacity as Integer
      if (snapshotDesiredCapacity != 0) {
        Integer seenCount = stage.context.zeroDesiredCapacityCount as Integer
        return seenCount >= MIN_ZERO_INSTANCE_RETRY_COUNT
      }
    }

    if (targetDesiredSize > instances.size()) {
      return false
    }

    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      log.info("${serverGroup.name}: Empty health providers supplied; considering it healthy")
      return true
    }

    def healthyCount = instances.count { Map instance ->
      HealthHelper.someAreUpAndNoneAreDown(instance, interestingHealthProviderNames)
    }

    log.info("${serverGroup.name}: Instances up check - healthy: $healthyCount, target: $targetDesiredSize")
    return healthyCount >= targetDesiredSize
  }

  static int calculateTargetDesiredSize(Stage stage, Map serverGroup) {
    Map<String, Integer> capacity = getServerGroupCapacity(stage, serverGroup)
    Integer targetDesiredSize = capacity.desired as Integer

    // Don't wait for spot instances to come up if the deployment strategy is None. All other deployment strategies rely on
    // confirming the new serverGroup is up and working correctly, so doing this is only safe with the None strategy
    // This should probably be moved to an AWS-specific part of the codebase
    if (serverGroup?.launchConfig?.spotPrice != null && stage.context.strategy == '') {
      return 0
    }

    if (stage.context.capacitySnapshot) {
      Integer snapshotCapacity = ((Map) stage.context.capacitySnapshot).desiredCapacity as Integer
      // if the server group is being actively scaled down, this operation might never complete,
      // so take the min of the latest capacity from the server group and the snapshot
      log.info("${serverGroup.name}: Calculating target desired size from snapshot (${snapshotCapacity}) and server group (${targetDesiredSize})")
      targetDesiredSize = Math.min(targetDesiredSize, snapshotCapacity)
    }

    if (stage.context.targetHealthyDeployPercentage != null) {
      Integer percentage = (Integer) stage.context.targetHealthyDeployPercentage
      if (percentage < 0 || percentage > 100) {
        throw new NumberFormatException("targetHealthyDeployPercentage must be an integer between 0 and 100")
      }
      targetDesiredSize = Math.ceil(percentage * targetDesiredSize / 100D) as Integer
      log.info("${serverGroup.name}: Calculating target desired size based on configured percentage (${percentage}) as ${targetDesiredSize} instances")
    } else if (stage.context.desiredPercentage != null) {
      Integer percentage = (Integer) stage.context.desiredPercentage
      targetDesiredSize = getDesiredInstanceCount(capacity, percentage)
    }
    log.info("${serverGroup.name}: Target desired size is ${targetDesiredSize}")
    targetDesiredSize
  }

  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    allInstancesMatch(stage, serverGroup, instances, interestingHealthProviderNames)
  }

  private static HealthCountSnapshot getHealthCountSnapshot(Stage stage, Map serverGroup) {
    HealthCountSnapshot snapshot = new HealthCountSnapshot()
    Collection<String> interestingHealthProviderNames = stage.context.interestingHealthProviderNames as Collection
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      snapshot.up = serverGroup.instances.size()
      return snapshot
    }
    serverGroup.instances.each { Map instance ->
      List<Map> healths = HealthHelper.filterHealths(instance, interestingHealthProviderNames)
      if (HealthHelper.someAreUpAndNoneAreDown(instance, interestingHealthProviderNames)) {
        snapshot.up++
      } else if (someAreDown(instance, interestingHealthProviderNames)) {
        snapshot.down++
      } else if (healths.any { it.state == 'OutOfService' } ) {
        snapshot.outOfService++
      } else if (healths.any { it.state == 'Starting' } ) {
        snapshot.starting++
      } else if (healths.every { it.state == 'Succeeded' } ) {
        snapshot.succeeded++
      } else if (healths.any { it.state == 'Failed' } ) {
        snapshot.failed++
      } else {
        snapshot.unknown++
      }
    }
    return snapshot
  }

  private static boolean someAreDown(Map instance, Collection<String> interestingHealthProviderNames) {
    List<Map> healths = HealthHelper.filterHealths(instance, interestingHealthProviderNames)

    if (!interestingHealthProviderNames && !healths) {
      // No health indications (and no specific providers to check), consider instance to be in an unknown state.
      return false
    }

    // no health indicators is indicative of being down
    return !healths || healths.any { it.state == 'Down' || it.state == 'OutOfService' }
  }

  /**
   * Determine the server group's current capacity.
   *
   * There is an edge-case with respect to AWS where the server group may be created at 0/0/0 and
   * immediately resized up.
   *
   * This method aims to generically detect these scenarios and use the target capacity of the
   * server group rather than 0/0/0.
   */
  private static Map<String, Integer> getServerGroupCapacity(Stage stage, Map serverGroup) {
    def serverGroupCapacity = serverGroup.capacity as Map<String, Integer>

    def initialTargetCapacity = getInitialTargetCapacity(stage, serverGroup)
    if (!initialTargetCapacity) {
      return serverGroupCapacity
    }

    if (serverGroup.capacity.max == 0 && initialTargetCapacity.max != 0) {
      log.info(
          "Overriding server group capacity (serverGroup: {}, initialTargetCapacity: {}, executionId: {})",
          "${serverGroup.region}:${serverGroup.name}",
          initialTargetCapacity,
          stage.execution.id
      )
      serverGroupCapacity = initialTargetCapacity
    }

    return serverGroupCapacity
  }

  /**
   * Fetch the new server group's initial capacity _if_ it was passed back from clouddriver.
   */
  private static Map<String, Integer> getInitialTargetCapacity(Stage stage, Map serverGroup) {
    def katoTasks = (stage.context."kato.tasks" as List<Map<String, Object>>)?.reverse()
    def katoTask = katoTasks?.find {
      ((List<Map>) it.getOrDefault("resultObjects", [])).any {
        it.containsKey("deployments")
      }
    }

    def deployments = ((List<Map>) katoTask?.getOrDefault("resultObjects", []))?.find {
      it.containsKey("deployments")
    }?.get("deployments") as List<Map>

    def deployment = deployments?.find {
      serverGroup.name == it.get("serverGroupName") && serverGroup.region == it.get("location")
    }

    return deployment?.capacity as Map<String, Integer>
  }
}
