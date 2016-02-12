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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
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
abstract class AbstractInstancesCheckTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = 10000
  long timeout = 7200000

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

  abstract protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames)

  @Override
  TaskResult execute(Stage stage) {
    String account = getCredentials(stage)
    Map<String, List<String>> serverGroups = getServerGroups(stage)

    if (!serverGroups || !serverGroups?.values()?.flatten()) {
      return new DefaultTaskResult(ExecutionStatus.TERMINAL)
    }
    Names names = Names.parseName(serverGroups.values().flatten()[0])
    try {
      def response = oortService.getCluster(names.app, account, names.cluster, getCloudProvider(stage))

      if (response.status != 200) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      }
      def cluster = objectMapper.readValue(response.body.in().text, Map)
      if (!cluster || !cluster.serverGroups) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      }
      Map<String, Boolean> seenServerGroup = serverGroups.values().flatten().collectEntries { [(it): false] }
      for (Map serverGroup in cluster.serverGroups) {
        String region = serverGroup.region
        String name = serverGroup.name

        def matches = serverGroups.find { String sgRegion, List<String> sgName ->
          return region == sgRegion && sgName.contains(name)
        }
        if (!matches) {
          continue
        }

        seenServerGroup[name] = true
        Collection<String> interestingHealthProviderNames = stage.context.interestingHealthProviderNames as Collection
        if (interestingHealthProviderNames == null) {
          interestingHealthProviderNames = stage.context?.appConfig?.interestingHealthProviderNames as Collection
        }
        def isComplete = hasSucceeded(stage, serverGroup, serverGroup.instances ?: [], interestingHealthProviderNames)
        if (!isComplete) {
          Map newContext = [:]
          if (seenServerGroup && !stage.context.capacitySnapshot) {
            newContext = [
              zeroDesiredCapacityCount: 0,
              capacitySnapshot        : [
                minSize        : serverGroup.capacity.min,
                desiredCapacity: serverGroup.capacity.desired,
                maxSize        : serverGroup.capacity.max
              ]
            ]
          }
          if (seenServerGroup) {
            if (serverGroup.capacity.desired == 0) {
              newContext.zeroDesiredCapacityCount = (stage.context.zeroDesiredCapacityCount ?: 0) + 1
            } else {
              newContext.zeroDesiredCapacityCount = 0
            }
          }
          return new DefaultTaskResult(ExecutionStatus.RUNNING, newContext)
        }
      }

      verifyServerGroupsExist(stage)

      if (seenServerGroup.values().contains(false)) {
        new DefaultTaskResult(ExecutionStatus.RUNNING)
      } else {
        new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      }
    } catch (RetrofitError e) {
      def retrofitErrorResponse = new RetrofitExceptionHandler().handle(stage.name, e)
      if (e.response?.status == 404) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      } else if (e.response?.status >= 500) {
        log.error("Unexpected retrofit error (${retrofitErrorResponse})")
        return new DefaultTaskResult(ExecutionStatus.RUNNING, [lastRetrofitException: retrofitErrorResponse])
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
          log.error("Server group '${region}:${it}' does not exist (forceCacheRefreshResult: ${forceCacheRefreshResult.stageOutputs}")
          throw new IllegalStateException("Server group '${region}:${it}' does not exist")
        }
      }
    }
  }
}
