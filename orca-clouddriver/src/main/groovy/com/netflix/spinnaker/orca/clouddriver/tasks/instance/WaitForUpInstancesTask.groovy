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

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import groovy.util.logging.Slf4j
import org.slf4j.MDC
import org.springframework.stereotype.Component

import java.util.stream.IntStream
import java.util.stream.Stream

@Component
@Slf4j
class WaitForUpInstancesTask extends AbstractInstancesCheckTask {

  private static final int MIN_ZERO_INSTANCE_RETRY_COUNT = 12

  // We do not want to fail before the server group has been created.
  boolean waitForUpServerGroup() {
    return true
  }

  @Override
  protected Map<String, Object> getAdditionalRunningStageContext(StageExecution stage, Map<String, Object> serverGroup) {
    Map<String, Object> additionalRunningStageContext = new HashMap<>()

    additionalRunningStageContext.put("targetDesiredSize", calculateTargetDesiredSize(stage, serverGroup))
    additionalRunningStageContext.put("lastCapacityCheck", getHealthCountSnapshot(stage, serverGroup))

    Map<String, Object> snapshot = (Map<String, Object>) stage.getContext().get("capacitySnapshot")

    if (snapshot == null || snapshot.isEmpty()) {
      Map<String, Integer> initialTargetCapacity = getServerGroupCapacity(stage, serverGroup)

      snapshot = new HashMap<>()
      snapshot.put("minSize", initialTargetCapacity.get("min"))
      snapshot.put("desiredCapacity", initialTargetCapacity.get("desired"))
      snapshot.put("maxSize", initialTargetCapacity.get("max"))

      additionalRunningStageContext.put("capacitySnapshot", snapshot)
    }

    return additionalRunningStageContext
  }

  public static boolean allInstancesMatch(StageExecution stage,
                                          Map<String, Object> serverGroup,
                                          List<Map<String, Object>> instances,
                                          Collection<String> interestingHealthProviderNames) {
    return allInstancesMatch(stage, serverGroup, instances, interestingHealthProviderNames, null)
  }

  public static boolean allInstancesMatch(StageExecution stage,
                                          Map<String, Object> serverGroup,
                                          List<Map<String, Object>> instances,
                                          Collection<String> interestingHealthProviderNames,
                                          Splainer parentSplainer) {
    Splainer splainer = parentSplainer == null ? new Splainer() : parentSplainer

    if (serverGroup == null) {
      splainer.add("short-circuiting out of allInstancesMatch because of null serverGroup")
      return false
    }

    String execId = stage.getExecution().getId()

    splainer.add(String.format("Instances up check for server group %s [executionId=%s, stagedId=%s]", serverGroup.get("name"), execId, execId))

    try {
      Object capacity = serverGroup.get("capacity")
      if (capacity == null || (capacity instanceof Map && capacity.isEmpty())) {
        splainer.add(String.format("short-circuiting out of allInstancesMatch because of empty capacity in serverGroup=%s", serverGroup))
        return false
      }

      Map<String, Object> capacitySnapshot = (Map<String, Object>) stage.getContext().get("capacitySnapshot")

      int targetDesiredSize = calculateTargetDesiredSize(stage, serverGroup, splainer)
      if (targetDesiredSize == 0 && capacitySnapshot != null && !capacitySnapshot.isEmpty()) {
        // if we've seen a non-zero value before, but we are seeing a target size of zero now, assume
        // it's a transient issue with edda unless we see it repeatedly
        Integer snapshotDesiredCapacity = capacitySnapshot.get("desiredCapacity") as Integer
        if (snapshotDesiredCapacity != 0) {
          Number seenCount = ((Number) stage.getContext().get("zeroDesiredCapacityCount"))
          boolean noLongerRetrying = seenCount >= MIN_ZERO_INSTANCE_RETRY_COUNT
          splainer.add(String.format("seeing targetDesiredSize=0 but capacitySnapshot=%s has non-0 desiredCapacity after %s/%s retries}", capacitySnapshot, seenCount, MIN_ZERO_INSTANCE_RETRY_COUNT))
          return noLongerRetrying
        }
      }

      if (targetDesiredSize > instances.size()) {
        splainer.add(String.format("short-circuiting out of allInstancesMatch because targetDesiredSize=%s > instances.size()=%s", targetDesiredSize, instances.size()))
        return false
      }

      if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
        splainer.add("empty health providers supplied; considering server group healthy")
        return true
      }

      long healthyCount = instances.stream()
          .filter({ Map instance ->
            HealthHelper.someAreUpAndNoneAreDownOrStarting(instance, interestingHealthProviderNames)
          })
          .count()

      splainer.add(String.format("returning healthyCount=%s >= targetDesiredSize=%s", healthyCount, targetDesiredSize))
      return healthyCount >= targetDesiredSize
    } finally {
      // if we have a parent splainer, then it's not our job to splain
      if (!parentSplainer) {
        splainer.splain()
      }
    }
  }

  public static int calculateTargetDesiredSize(StageExecution stage, @Nonnull Map<String, Object> serverGroup) {
    return calculateTargetDesiredSize(stage, serverGroup, NOOPSPLAINER)
  }

  public static int calculateTargetDesiredSize(StageExecution stage, @Nonnull Map<String, Object> serverGroup, Splainer splainer) {
    Map<String, Object> context = stage.getContext()

    // Don't wait for spot instances to come up if the deployment strategy is None. All other deployment strategies rely on
    // confirming the new serverGroup is up and working correctly, so doing this is only safe with the None strategy
    // This should probably be moved to an AWS-specific part of the codebase
    boolean hasSpotPrice = Optional.ofNullable(serverGroup)
        .map({ (Map) it.get("launchConfig") })
        .map({ it.get("spotPrice") })
        .isPresent()
    if (hasSpotPrice && "".equals(context.get("strategy"))) {
      splainer.add("setting targetDesiredSize=0 because the server group has a spot price configured and the strategy is None")
      return 0
    }

    Map<String, Integer> currentCapacity = getServerGroupCapacity(stage, serverGroup)
    Integer targetDesiredSize

    if (useConfiguredCapacity(stage, currentCapacity)) {
      targetDesiredSize = ((Map<String, Integer>) context.get("capacity")).get("desired") as Integer
      splainer.add(String.format("setting targetDesiredSize=%s from the configured stage context.capacity=%s", targetDesiredSize, context.capacity))
    } else {
      targetDesiredSize = currentCapacity.get("desired") as Integer
      splainer.add(String.format("setting targetDesiredSize=%s from the desired size in current serverGroup capacity=%s", targetDesiredSize, currentCapacity))
    }

    Integer percentage = (Integer) context.get("targetHealthyDeployPercentage")
    if (percentage != null) {

      if (percentage < 0 || percentage > 100) {
        throw new NumberFormatException("targetHealthyDeployPercentage must be an integer between 0 and 100")
      }

      Integer newTargetDesiredSize = Math.round(percentage * targetDesiredSize / 100D) as Integer
      if ((newTargetDesiredSize == 0) && (percentage != 0) && (targetDesiredSize > 0)) {
        // Unless the user specified they want 0% or we actually have 0 instances in the server group,
        // never allow 0 instances due to rounding
        newTargetDesiredSize = 1
      }
      splainer.add(String.format("setting targetDesiredSize=%s based on configured targetHealthyDeployPercentage=%s%% of previous targetDesiredSize=%s", newTargetDesiredSize, percentage, targetDesiredSize))
      targetDesiredSize = newTargetDesiredSize
    } else {
      percentage = (Integer) context.get("desiredPercentage")
      if (percentage != null) {
        targetDesiredSize = WaitingForInstancesTaskHelper.getDesiredInstanceCount(currentCapacity, percentage)
        splainer.add(String.format("setting targetDesiredSize=%s based on desiredPercentage=%s%% of capacity=%s", targetDesiredSize, percentage, currentCapacity))
      }
    }

    return targetDesiredSize
  }


  private static final Set<String> CAPACITY_KEYS = Set.of("min", "max", "desired")
  // If either the configured capacity or current serverGroup has autoscaling disabled, calculate
  // targetDesired from the configured capacity. This relaxes the need for clouddriver onDemand
  // cache updates while resizing serverGroups.
  static boolean useConfiguredCapacity(StageExecution stage, Map<String, Integer> current) {

    if (current.get("desired") == null) {
      return true
    }
    Map<String, Object> contextCapacity = (Map<String, Object>) stage.getContext().get("capacity")

    if (contextCapacity == null || contextCapacity.get("desired") == null) {
      return false
    }

    // need convert values to integers but other keys may be present
    Map<String, Integer> configured = new HashMap<>()
    contextCapacity.forEach({ k, v ->
      if (CAPACITY_KEYS.contains(k)) {
        Integer value = null
        if (v instanceof Number) {
          value = ((Number) v).intValue()
        } else if (v != null) {
          value = Integer.parseInt(v.toString())
        }
        configured.put(k, value)
      }
    })
    return (configured.get("min") == configured.get("max") && configured.get("min") == configured.get("desired")) ||
        (current.get("min") == current.get("max") && current.get("min") == current.get("desired"))
  }

  @Override
  protected Map<String, List<String>> getServerGroups(StageExecution stage) {
    return WaitingForInstancesTaskHelper.extractServerGroups(stage)
  }

  @Override
  protected boolean hasSucceeded(StageExecution stage, Map<String, Object> serverGroup, List<Map<String, Object>> instances, Collection<String> interestingHealthProviderNames) {
    allInstancesMatch(stage, serverGroup, instances, interestingHealthProviderNames)
  }

  private static HealthCountSnapshot getHealthCountSnapshot(StageExecution stage, Map<String, Object> serverGroup) {
    HealthCountSnapshot snapshot = new HealthCountSnapshot()
    Collection<String> interestingHealthProviderNames = stage.context.interestingHealthProviderNames as Collection
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      snapshot.up = serverGroup.instances.size()
      return snapshot
    }
    serverGroup.get("instances").each { Map instance ->
      List<Map<String, Object>> healths = HealthHelper.filterHealths(instance, interestingHealthProviderNames)
      if (HealthHelper.someAreUpAndNoneAreDownOrStarting(instance, interestingHealthProviderNames)) {
        snapshot.up++
      } else if (someAreDown(instance, interestingHealthProviderNames)) {
        snapshot.down++
      } else {
        if (healths.stream().anyMatch({ "OutOfService".equals(it.get("state")) })) {
          snapshot.outOfService++
        } else if (healths.stream().anyMatch({ "Starting".equals(it.get("state")) })) {
          snapshot.starting++
        } else if (healths.stream().allMatch({ "Succeeded".equals(it.get("state")) })) {
          snapshot.succeeded++
        } else if (healths.stream().anyMatch({ "Failed".equals(it.get("state")) })) {
          snapshot.failed++
        } else {
          snapshot.unknown++
        }
      }
    }
    return snapshot
  }

  private static boolean someAreDown(Map instance, Collection<String> interestingHealthProviderNames) {
    List<Map<String, Object>> healths = HealthHelper.filterHealths(instance, interestingHealthProviderNames)

    if (!interestingHealthProviderNames && !healths) {
      // No health indications (and no specific providers to check), consider instance to be in an unknown state.
      return false
    }

    // no health indicators is indicative of being down
    return !healths || healths.stream().anyMatch({ it.state == 'Down' || it.state == 'OutOfService' })
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
  protected static Map<String, Integer> getServerGroupCapacity(StageExecution stage, Map<String, Object> serverGroup) {
    Map<String, Integer> serverGroupCapacity = serverGroup.get("capacity") as Map<String, Integer>

    String execId = stage.getExecution().getId()

    String cloudProvider = (String) stage.getContext().get("cloudProvider")

    String taskStartTime = MDC.get("taskStartTime")
    if (taskStartTime != null) {
      if (System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10) > Long.valueOf(taskStartTime)) {
        // expectation is reconciliation has happened within 10 minutes and that the
        // current server group capacity should be preferred
        log.warn(
            "Short circuiting initial target capacity determination after 10 minutes (serverGroup: {}:{}:{}, executionId: {})",
            cloudProvider,
            serverGroup.get("region"),
            serverGroup.get("name"),
            execId
        )
        return serverGroupCapacity
      }
    }

    def initialTargetCapacity = getInitialTargetCapacity(stage, serverGroup)
    if (!initialTargetCapacity) {
      log.debug(
          "Unable to determine initial target capacity (serverGroup: {}:{}:{}, executionId: {})",
          cloudProvider,
          serverGroup.get("region"),
          serverGroup.get("name"),
          execId
      )
      return serverGroupCapacity
    }

    if ((serverGroupCapacity.get("max") == 0 && initialTargetCapacity.get("max") != 0) ||
        (serverGroupCapacity.get("desired") == 0 && initialTargetCapacity.get("desired") > 0)) {
      log.info(
          "Overriding server group capacity (serverGroup: {}:{}:{}, initialTargetCapacity: {}, executionId: {})",
          cloudProvider,
          serverGroup.get("region"),
          serverGroup.get("name"),
          initialTargetCapacity,
          execId
      )
      serverGroupCapacity = initialTargetCapacity
    }

    log.debug(
        "Determined server group capacity (serverGroup: {}:{}:{}, serverGroupCapacity: {}, initialTargetCapacity: {}, executionId: {}",
        cloudProvider,
        serverGroup.get("region"),
        serverGroup.get("name"),
        serverGroupCapacity,
        initialTargetCapacity,
        execId
    )

    return serverGroupCapacity
  }

  /**
   * Fetch the new server group's initial capacity _if_ it was passed back from clouddriver.
   */
  static Map<String, Integer> getInitialTargetCapacity(StageExecution stage, Map<String, Object> serverGroup) {
    List<Map<String, Object>> katoTasks = (List<Map<String, Object>>) stage.getContext().get("kato.tasks")

    if (katoTasks == null) {
      return null
    }

    String name = (String) serverGroup.get("name")
    String region = (String) serverGroup.get("region")

    Optional<List<Map<String, Object>>> maybeDeployments = reverseStream(katoTasks)
        .map({
          List<Map> results = (List<Map<String, Object>>) it.get("resultObjects")
          if (results == null) {
            return null
          }
          results.stream()
              .map({ (List<Map<String, Object>>) it.get("deployments") })
              .filter({ it != null })
              .findFirst()
              .orElse(null)
        })
        .filter({ it != null })
        .findFirst()

    // find the last resultObjects with deployments
    Map<String, Integer> result = maybeDeployments
        .flatMap({ deployments ->
          deployments.stream()
              .filter({ deployment ->
                Objects.equals(name, deployment.get("serverGroupName")) && Objects.equals(region, deployment.get("location"))
              })
              .map({ deployment ->
                deployment.get("capacity") as Map<String, Integer>
              })
              .filter({ it != null })
              .findFirst()
        })
        .orElse(null)
    return result
  }

  static <T> Stream<T> reverseStream(List<T> list) {
    if (list.isEmpty()) {
      return Stream.empty()
    } else if (list.size() == 1) {
      return list.stream()
    }
    return IntStream.range(1, list.size() + 1).mapToObj({ list.get(list.size() - it) })
  }

  public static class Splainer {
    List<String> messages = new ArrayList<>()

    Splainer add(String message) {
      messages.add(message)
      return this
    }

    void splain() {
      log.info(String.join("\n  - ", messages))
    }
  }

  private static class NoopSplainer extends Splainer {
    NoopSplainer add(String message) { this }

    void splain() {}
  }

  private static NoopSplainer NOOPSPLAINER = new NoopSplainer()

  static class HealthCountSnapshot {
    int up
    int down
    int outOfService
    int starting
    int succeeded
    int failed
    int unknown
  }
}
