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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractWaitingForInstancesTask
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
class WaitForRequiredInstancesDownTask extends AbstractWaitingForInstancesTask {
  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true
    }

    def targetDesiredSize = instances.size()

    // During a rolling red/black we want a percentage of instances to be disabled.
    def desiredPercentage = stage.context.desiredPercentage
    if (desiredPercentage != null) {
      List<String> skippedIds = getSkippedInstances(stage)
      List<Map> skippedInstances = instances.findAll { skippedIds.contains(it.name) }
      List<Map> instancesToDisable = getInstancesToDisable(stage, instances)

      if (!skippedInstances.isEmpty()) {
        List<Map> skippedButNotDown = skippedInstances.findAll {
          !HealthHelper.someAreDownAndNoneAreUp(it, interestingHealthProviderNames)
        }
        Map<String, List<Map>> skippedInstanceHealths = skippedButNotDown.collectEntries { instance ->
          [(instance.name): HealthHelper.filterHealths(instance, interestingHealthProviderNames)]
        }
        if (!skippedInstanceHealths.isEmpty()) {
          log.debug(
            "Health for instances in {} that clouddriver skipped deregistering but are " +
              "reporting as up: {} (executionId: {})",
            serverGroup.name,
            skippedInstanceHealths,
            stage.execution.id
          )
        }
      }

      if (instancesToDisable) {
        /**
         * Ensure that any explicitly supplied (by clouddriver!) instance ids have been disabled.
         *
         * If no instance ids found, fall back to using `desiredPercentage` to calculate how many instances
         * should be disabled.
         */
        def instancesAreDisabled = instancesToDisable.every { instance ->
          return HealthHelper.someAreDownAndNoneAreUp(instance, interestingHealthProviderNames)
        }

        log.debug(
          "{} {}% of {}: {} (executionId: {})",
          instancesAreDisabled ? "Disabled" : "Disabling",
          desiredPercentage,
          serverGroup.name,
          instancesToDisable.collect { it.name }.join(", "),
          stage.execution.id
        )

        return instancesAreDisabled
      }

      Map capacity = (Map) serverGroup.capacity
      Integer percentage = (Integer) desiredPercentage
      targetDesiredSize = getDesiredInstanceCount(capacity, percentage)
    }

    // We need at least target instances to be disabled.
    return instances.count { instance ->
      return HealthHelper.someAreDownAndNoneAreUp(instance, interestingHealthProviderNames)
    } >= targetDesiredSize
  }

  static List<String> getSkippedInstances(Stage stage, List<Map> results = []) {
    def resultObjects = results.isEmpty() ? getKatoResults(stage) : results
    def skippedInstances = resultObjects.find {
      it.containsKey("discoverySkippedInstanceIds")
    }?.discoverySkippedInstanceIds ?: []

    return skippedInstances as List<String>
  }

  static List<Map> getInstancesToDisable(Stage stage, List<Map> instances) {
    def resultObjects = getKatoResults(stage)

    if (!resultObjects.isEmpty()) {
      def instanceIdsToDisable = resultObjects.find {
        it.containsKey("instanceIdsToDisable")
      }?.instanceIdsToDisable ?: []

      def skippedInstances = getSkippedInstances(stage, resultObjects)

      return instances.findAll {
        instanceIdsToDisable.contains(it.name) &&
          !skippedInstances.contains(it.name)
      }
    }

    return []
  }

  static List<Map> getKatoResults(Stage stage) {
    TaskId lastTaskId = stage.context."kato.last.task.id" as TaskId
    def katoTasks = stage.context."kato.tasks" as List<Map>
    def lastKatoTask = katoTasks.find { it.id.toString() == lastTaskId.id }

    if (lastKatoTask) {
      def resultObjects = lastKatoTask.resultObjects as List<Map>
      return resultObjects
    } else {
      return []
    }
  }

}
