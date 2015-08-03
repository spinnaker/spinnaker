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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Slf4j
class WaitForUpInstancesTask extends AbstractWaitingForInstancesTask {

  static boolean allInstancesMatch(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    if (!(serverGroup?.asg)) {
      return false
    }
    int targetDesiredSize = calculateTargetDesiredSize(stage, serverGroup)
    if (targetDesiredSize > instances.size()) {
      return false
    }

    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      log.info("${serverGroup.name}: Empty health providers supplied; considering it healthy")
      return true
    }

    def healthyCount = instances.count { Map instance ->
      def healths = interestingHealthProviderNames ? instance.health.findAll { Map health ->
        health.type in interestingHealthProviderNames
      } : instance.health
      boolean someAreUp = healths.any { Map health -> health.state == 'Up' }
      if (interestingHealthProviderNames?.contains("Amazon")) {
        // given that Amazon health never reports as 'Up' (only 'Unknown') we can only verify it isn't 'Down'.
        someAreUp = someAreUp || healths.any { Map health -> health.type == 'Amazon' && health.state != 'Down' }
      }
      boolean noneAreDown = !healths.any { Map health -> health.state == 'Down' }
      someAreUp && noneAreDown
    }
    log.info("${serverGroup.name}: Instances up check - healthy: $healthyCount, target: $targetDesiredSize")
    return healthyCount >= targetDesiredSize
  }

  private static int calculateTargetDesiredSize(Stage stage, Map serverGroup) {
    // favor using configured target capacity whenever available (rather than in-progress asg's desiredCapacity)
    Map asg = (Map) serverGroup.asg
    Integer targetDesiredSize = asg.desiredCapacity as Integer

    if (stage.context.capacitySnapshot) {
      Integer snapshotCapacity = ((Map) stage.context.capacitySnapshot).desiredCapacity as Integer
      // if the ASG is being actively scaled down, this operation might never complete,
      // so take the min of the latest capacity from the ASG and the snapshot
      log.info("${serverGroup.name}: Calculating target desired size from snapshot (${snapshotCapacity}) and ASG (${targetDesiredSize})")
      targetDesiredSize = Math.min(targetDesiredSize, snapshotCapacity)
    }

    if (stage.context.targetHealthyDeployPercentage != null) {
      Integer percentage = (Integer) stage.context.targetHealthyDeployPercentage
      if (percentage < 0 || percentage > 100) {
        throw new NumberFormatException("targetHealthyDeployPercentage must be an integer between 0 and 100")
      }
      targetDesiredSize = Math.ceil(percentage * targetDesiredSize / 100D) as Integer
      log.info("${serverGroup.name}: Calculating target desired size based on configured percentage (${percentage}) as ${targetDesiredSize} instances")
    }
    log.info("${serverGroup.name}: Target desired size is ${targetDesiredSize}")
    targetDesiredSize
  }

  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    allInstancesMatch(stage, serverGroup, instances, interestingHealthProviderNames)
  }

}
