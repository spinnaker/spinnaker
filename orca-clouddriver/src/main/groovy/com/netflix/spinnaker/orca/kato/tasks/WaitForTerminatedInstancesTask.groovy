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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class WaitForTerminatedInstancesTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = 10000
  long timeout = 3600000

  @Autowired
  OortHelper oortHelper

  @Override
  TaskResult execute(Stage stage) {
    List<String> instanceIds = stage.context.'terminate.remaining.ids' ?: stage.context.'terminate.instance.ids'

    if (!instanceIds || !instanceIds.size()) {
      throw new IllegalStateException("no instanceIds for termination check")
    }

    String serverGroupName = stage.context.serverGroupName ?: stage.context.asgName

    List<String> remainingInstanceIds = serverGroupName ?
      getRemainingInstancesFromServerGroup(stage, serverGroupName, instanceIds) :
      getRemainingInstancesFromSearch(stage, instanceIds)

    if (remainingInstanceIds) {
      return new DefaultTaskResult(ExecutionStatus.RUNNING, ['terminate.remaining.ids': remainingInstanceIds])
    }

    return DefaultTaskResult.SUCCEEDED
  }

  List<String> getRemainingInstancesFromSearch(Stage stage, List<String> instanceIds) {
    String cloudProvider = getCloudProvider(stage)
    Collections.shuffle(instanceIds)
    int firstNotTerminated = instanceIds.findIndexOf { String instanceId ->
      try {
        def searchResult = oortHelper.getSearchResults(instanceId, "instances", cloudProvider)
        if (!searchResult || searchResult.size() != 1) {
          return true
        }
        Map searchResultSet = (Map) searchResult[0]
        if (searchResultSet.totalMatches != 0) {
          return true
        }
        return false
      } catch (RetrofitError ignored) {
        return true
      }
    }

    if (firstNotTerminated == -1) {
      return Collections.emptyList()
    }

    return instanceIds.subList(firstNotTerminated, instanceIds.size())
  }

  List<String> getRemainingInstancesFromServerGroup(Stage stage, String serverGroupName, List<String> instanceIds) {
    String account = stage.context.'terminate.account.name'
    String location = stage.context.'terminate.region'
    String cloudProvider = getCloudProvider(stage)

    def tsg = oortHelper.getTargetServerGroup(account, serverGroupName, location, cloudProvider)
    tsg.map { TargetServerGroup sg ->
      sg.instances.findResults { instanceIds.contains(it.name) ? it.name : null }
    }.orElseThrow {
      new IllegalStateException("ServerGroup not found for $cloudProvider/$account/$location/$serverGroupName")
    }
  }
}
