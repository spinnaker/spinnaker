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

import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper

import java.time.Duration
import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError

@Slf4j
abstract class AbstractInstancesCheckTask extends AbstractCloudProviderAwareTask implements OverridableTimeoutRetryableTask {
  long backoffPeriod = TimeUnit.SECONDS.toMillis(10)
  long timeout = TimeUnit.HOURS.toMillis(2)
  long serverGroupWaitTime = TimeUnit.MINUTES.toMillis(10)

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ServerGroupCacheForceRefreshTask serverGroupCacheForceRefreshTask

  @Autowired
  OortHelper oortHelper

  /**
   * Other components (namely: deck) require this map to be region --> serverGroups, rather than location -->
   * serverGroups, regardless of cloud provider.
   *
   * @return A map of region --> list of serverGroup names.
   */
  abstract protected Map<String, List<String>> getServerGroups(Stage stage)

  abstract
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames)

  protected Map getAdditionalRunningStageContext(Stage stage, Map serverGroup) {
    [:]
  }

  // When waiting for up instances during a "Deploy" stage, it is OK for the server group to not exist coming into this
  // task. Instead of failing on a missing server group, we retry the stage until it either succeeds, fails, or times out.
  // For the other uses of this task, we will fail if the server group doesn't exist.
  boolean waitForUpServerGroup() {
    return false
  }

  @Override
  long getDynamicBackoffPeriod(Stage stage, Duration taskDuration) {
    if (taskDuration.toMillis() > TimeUnit.MINUTES.toMillis(60)) {
      // task has been running > 60min, drop retry interval to every 2 minutes
      return Math.max(backoffPeriod, TimeUnit.SECONDS.toMillis(120))
    } else if (taskDuration.toMillis() > TimeUnit.MINUTES.toMillis(30)) {
      // task has been running > 30min, drop retry interval to every 60s
      return Math.max(backoffPeriod, TimeUnit.SECONDS.toMillis(60))
    }

    return backoffPeriod
  }

  @Override
  TaskResult execute(Stage stage) {
    String account = getCredentials(stage)
    Map<String, List<String>> serverGroupsByRegion = getServerGroups(stage)

    if (!serverGroupsByRegion || !serverGroupsByRegion?.values()?.flatten()) {
      return TaskResult.ofStatus(ExecutionStatus.TERMINAL)
    }

    try {
      Moniker moniker = MonikerHelper.monikerFromStage(stage)
      def serverGroups = fetchServerGroups(account, getCloudProvider(stage), serverGroupsByRegion, moniker)
      if (!serverGroups) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      Map<String, Boolean> seenServerGroup = serverGroupsByRegion.values().flatten().collectEntries { [(it): false] }
      for (Map serverGroup in serverGroups) {
        String region = serverGroup.region
        String name = serverGroup.name

        def matches = serverGroupsByRegion.find { String sgRegion, List<String> sgName ->
          return region == sgRegion && sgName.contains(name)
        }
        if (!matches) {
          continue
        }

        seenServerGroup[name] = true
        Collection<String> interestingHealthProviderNames = stage.context.interestingHealthProviderNames as Collection
        def isComplete = hasSucceeded(stage, serverGroup, serverGroup.instances ?: [], interestingHealthProviderNames)
        if (!isComplete) {
          Map newContext = getAdditionalRunningStageContext(stage, serverGroup)
          if (seenServerGroup) {
            if (!stage.context.capacitySnapshot) {
              newContext.zeroDesiredCapacityCount = 0
              newContext.capacitySnapshot = newContext.capacitySnapshot ?: [
                  minSize        : serverGroup.capacity.min,
                  desiredCapacity: serverGroup.capacity.desired,
                  maxSize        : serverGroup.capacity.max
              ]
            }

            if (serverGroup.capacity.desired == 0) {
              newContext.zeroDesiredCapacityCount = (stage.context.zeroDesiredCapacityCount ?: 0) + 1
            } else {
              newContext.zeroDesiredCapacityCount = 0
            }
          }
          newContext.currentInstanceCount = serverGroup.instances?.size() ?: 0
          return TaskResult.builder(ExecutionStatus.RUNNING).context(newContext).build()
        }
      }

      try {
        verifyServerGroupsExist(stage)
      } catch (MissingServerGroupException e) {
        if (waitForUpServerGroup()) {
          def now = System.currentTimeMillis()
          def runningTask = stage.tasks.find { task -> task.status == ExecutionStatus.RUNNING }
          if (!runningTask) {
            throw new IllegalStateException("Unable to find currently running task. This is likely a problem with Spinnaker itself.")
          } else if (now - runningTask.startTime > serverGroupWaitTime) {
            log.info "Waited over ${TimeUnit.MILLISECONDS.toMinutes(serverGroupWaitTime)} minutes for the server group to appear."
            throw e
          }
          log.info "Waiting for server group to show up, ignoring error: $e.message"
          return TaskResult.ofStatus(ExecutionStatus.RUNNING)
        } else {
          throw e
        }
      }

      if (seenServerGroup.values().contains(false)) {
        TaskResult.ofStatus(ExecutionStatus.RUNNING)
      } else {
        TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
      }
    } catch (RetrofitError e) {
      def retrofitErrorResponse = new RetrofitExceptionHandler().handle(stage.name, e)
      if (e.response?.status == 404) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      } else if (e.response?.status >= 500) {
        log.error("Unexpected retrofit error (${retrofitErrorResponse})")
        return TaskResult.builder(ExecutionStatus.RUNNING).context([lastRetrofitException: retrofitErrorResponse]).build()
      }

      throw e
    }
  }

  /**
   * Assert that the server groups being acted upon still exist.
   *
   * Will raise an exception in the event that a server group is being modified and is destroyed by an external process.
   */
  void verifyServerGroupsExist(Stage stage) {
    def forceCacheRefreshResult = serverGroupCacheForceRefreshTask.execute(stage)

    Map<String, List<String>> serverGroups = getServerGroups(stage)
    String account = getCredentials(stage)

    serverGroups.each { String region, List<String> serverGroupNames ->
      serverGroupNames.each {
        if (!oortHelper.getTargetServerGroup(account, it, region, getCloudProvider(stage)).isPresent()) {
          log.error("Server group '${region}:${it}' does not exist (forceCacheRefreshResult: ${forceCacheRefreshResult.context}")
          throw new MissingServerGroupException("Server group '${region}:${it}' does not exist")
        }
      }
    }
  }

  protected final List<Map> fetchServerGroups(String account,
                                              String cloudProvider,
                                              Map<String, List<String>> serverGroupsByRegion,
                                              Moniker moniker) {
    if (serverGroupsByRegion.values().flatten().size() > 1) {
      Names names = Names.parseName(serverGroupsByRegion.values().flatten()[0])
      def appName = moniker?.app ?: names.app
      def clusterName = moniker?.cluster ?: names.cluster
      def response = oortService.getCluster(appName, account, clusterName, cloudProvider)
      def cluster = objectMapper.readValue(response.body.in().text, Map)
      return cluster.serverGroups ?: []
    } else {
      def region = serverGroupsByRegion.keySet()[0]
      def serverGroupName = serverGroupsByRegion[region][0]

      try {
        def response = oortService.getServerGroup(account, region, serverGroupName)
        return [objectMapper.readValue(response.body.in().text, Map)]
      } catch (RetrofitError e) {
        if (e.response?.status != 404 || waitForUpServerGroup()) {
          throw e
        }

        throw new MissingServerGroupException("Server group '${region}:${serverGroupName}' does not exist")
      }
    }
  }

  class MissingServerGroupException extends IllegalStateException {
    MissingServerGroupException(String message) {
      super(message)
    }
  }
}
