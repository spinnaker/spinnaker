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

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.model.Health
import com.netflix.spinnaker.orca.clouddriver.model.Instance
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractInstancesCheckTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitingForInstancesTaskHelper
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

import java.util.stream.Collectors

@Component
@Slf4j
public class WaitForRequiredInstancesDownTask extends AbstractInstancesCheckTask {
  @Override
  protected Map<String, List<String>> getServerGroups(StageExecution stage) {
    return WaitingForInstancesTaskHelper.extractServerGroups(stage)
  }

  @Override
  protected boolean hasSucceeded(StageExecution stage, ServerGroup serverGroup, List<Instance> instances, Collection<String> interestingHealthProviderNames) {
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true
    }

    int targetDesiredSize = instances.size()

    // During a rolling red/black we want a percentage of instances to be disabled.
    Object desiredPercentage = stage.getContext().get("desiredPercentage")
    if (desiredPercentage != null) {
      List<String> skippedIds = getSkippedInstances(stage)

      List<Instance> skippedInstances = skippedIds.isEmpty()
          ? List.of()
          : instances.stream().filter({ skippedIds.contains(it.getName()) }).collect(Collectors.toList())

      List<Instance> instancesToDisable = getInstancesToDisable(stage, instances)

      if (!skippedInstances.isEmpty()) {
        List<Instance> skippedButNotDown = skippedInstances.stream().
            filter({ !HealthHelper.someAreDownAndNoneAreUp(it, interestingHealthProviderNames) })
            .collect(Collectors.toList())

        Map<String, List<Health>> skippedInstanceHealths = new HashMap<>()

        skippedButNotDown.stream().forEach({ instance ->
          skippedInstanceHealths.put(instance.getName(), HealthHelper.filterHealths(instance, interestingHealthProviderNames))
        })

        if (!skippedInstanceHealths.isEmpty()) {
          log.debug(
              "Health for instances in {} that clouddriver skipped deregistering but are " +
                  "reporting as up: {} (executionId: {})",
              serverGroup.getName(),
              skippedInstanceHealths,
              stage.getExecution().getId())
        }
      }

      if (!instancesToDisable.isEmpty()) {
        /**
         * Ensure that any explicitly supplied (by clouddriver!) instance ids have been disabled.
         *
         * If no instance ids found, fall back to using `desiredPercentage` to calculate how many instances
         * should be disabled.
         */
        boolean instancesAreDisabled = instancesToDisable.stream().allMatch({ instance ->
          return HealthHelper.someAreDownAndNoneAreUp(instance, interestingHealthProviderNames)
        })

        log.debug(
            "{} {}% of {}: {} (executionId: {})",
            instancesAreDisabled ? "Disabled" : "Disabling",
            desiredPercentage,
            serverGroup.getName(),
            instancesToDisable.stream().map({ it.getName() }).collect(Collectors.joining(", ")),
            stage.getExecution().getId()
        )

        return instancesAreDisabled
      }

      ServerGroup.Capacity capacity = serverGroup.getCapacity()
      Integer percentage = (Integer) desiredPercentage
      targetDesiredSize = WaitingForInstancesTaskHelper.getDesiredInstanceCount(capacity, percentage)
    }

    // We need at least target instances to be disabled.
    return instances.stream()
        .filter({ instance -> HealthHelper.someAreDownAndNoneAreUp(instance, interestingHealthProviderNames) })
        .count() >= targetDesiredSize
  }

  private static List<String> getSkippedInstances(StageExecution stage, List<Map> results = List.of()) {
    List<Map> resultObjects = results.isEmpty() ? getKatoResults(stage) : results
    List<String> skippedInstances = resultObjects.stream()
        .filter({ it.containsKey("discoverySkippedInstanceIds") })
        .map({ it.get("discoverySkippedInstanceIds") as List<String> })
        .filter({ it != null })
        .findFirst()
        .orElse(List.of())

    return skippedInstances
  }

  protected static List<Instance> getInstancesToDisable(StageExecution stage, List<Instance> instances) {
    List<Map> resultObjects = getKatoResults(stage)

    if (!resultObjects.isEmpty()) {
      Collection<String> instanceIdsToDisable = resultObjects.stream()
          .filter({ it.containsKey("instanceIdsToDisable") })
          .map({ it.get("instanceIdsToDisable") as Collection<String> })
          .filter({ it != null })
          .findFirst()
          .orElse(List.of())

      List<String> skippedInstances = getSkippedInstances(stage, resultObjects)

      return instances.stream()
          .filter({instance ->
            String name = instance.getName()
            return instanceIdsToDisable.contains(name) && !skippedInstances.contains(name)
          })
          .collect(Collectors.toList())
    }
    return List.of()
  }

  private static List<Map> getKatoResults(StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    TaskId lastTaskId = context.get("kato.last.task.id") as TaskId //TODO: this coercion is gross
    List<Map> katoTasks = context.get("kato.tasks") as List<Map>

    if (katoTasks != null && lastTaskId != null) {
      Map lastKatoTask = katoTasks.stream()
          .filter({ it.get("id").toString().equals(lastTaskId.getId()) })
          .findFirst()
          .orElse(null)

      if (lastKatoTask != null && !lastKatoTask.isEmpty()) {
        def resultObjects = lastKatoTask.get("resultObjects") as List<Map>
        return resultObjects
      }
    }
    return List.of()
  }
}
