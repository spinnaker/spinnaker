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

package com.netflix.spinnaker.orca.kato.tasks.rollingpush


import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.model.Instance
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class DetermineTerminationCandidatesTask implements Task {

  @Autowired
  CloudDriverService cloudDriverService
  @Autowired(required = false)
  List<ServerGroupInstanceIdCollector> serverGroupInstanceIdCollectors = []

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    def stageData = stage.mapTo(StageData)
    ServerGroup serverGroup = cloudDriverService.getServerGroupFromCluster(stageData.application, stageData.account, stageData.cluster, stage.context.asgName, stage.context.region, stage.context.cloudProvider ?: 'aws')
    boolean ascending = stage.context.termination?.order != 'newest'
    List<Instance> serverGroupInstances = serverGroup.instances.sort { ascending ? it.launchTime : -it.launchTime }
    // need to use id instead of instanceIds for titus as the titus API doesn't yet support this yet.
    def knownInstanceIds = getServerGroupInstanceIdCollector(stage)
        .flatMap { it.collect(serverGroupInstances) }
        .orElseGet { serverGroupInstances*.instanceId }
    def terminationInstancePool = knownInstanceIds
    if (stage.context.termination?.instances) {
      terminationInstancePool = knownInstanceIds.intersect(stage.context.termination?.instances)
      if (stage.context.termination.order == 'given') {
        terminationInstancePool = terminationInstancePool.sort { stage.context.termination.instances.indexOf(it) }
      }
    }
    int totalRelaunches = getNumberOfRelaunches(stage.context.termination, terminationInstancePool.size())
    def terminationInstanceIds = terminationInstancePool.take(totalRelaunches)
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([terminationInstanceIds: terminationInstanceIds, knownInstanceIds: knownInstanceIds, skipRemainingWait: true, waitTime: stage.context.termination?.waitTime ?: 0 ]).build()
  }

  int getNumberOfRelaunches(Map termination, int totalAsgSize) {
    if (termination == null || termination.relaunchAllInstances || termination.totalRelaunches == null) {
      return totalAsgSize
    }

    return termination.totalRelaunches as Integer
  }

  private Optional<ServerGroupInstanceIdCollector> getServerGroupInstanceIdCollector(StageExecution stage) {
    return Optional.ofNullable((String) stage.getContext().get("cloudProvider")).flatMap { cloudProvider ->
      serverGroupInstanceIdCollectors.stream().filter { it.supports(cloudProvider) }.findFirst()
    }
  }
}
