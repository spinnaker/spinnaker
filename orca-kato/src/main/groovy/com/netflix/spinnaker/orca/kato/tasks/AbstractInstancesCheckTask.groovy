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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractInstancesCheckTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = 600000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  abstract protected Map<String, List<String>> getServerGroups(Stage stage)

  abstract protected boolean hasSucceeded(List instances)

  @Override
  TaskResult execute(Stage stage) {
    String account = stage.context."account.name"
    if (stage.context.account && !account) {
      account = stage.context.account
    } else if (stage.context.credentials && !account) {
      account = stage.context.credentials
    }

    Map<String, List<String>> serverGroups = getServerGroups(stage)

    if (!serverGroups || !serverGroups?.values()?.flatten()) {
      return new DefaultTaskResult(PipelineStatus.FAILED)
    }
    Names names = Names.parseName(serverGroups.values().flatten()[0])
    def response = oortService.getCluster(names.app, account, names.cluster)
    if (response.status != 200) {
      return new DefaultTaskResult(PipelineStatus.RUNNING)
    }
    def cluster = objectMapper.readValue(response.body.in().text, Map)
    if (!cluster || !cluster.serverGroups) {
      return new DefaultTaskResult(PipelineStatus.RUNNING)
    }
    Map<String, Boolean> seenServerGroup = serverGroups.values().flatten().collectEntries { [(it): false] }
    for (Map serverGroup in cluster.serverGroups) {
      String region = serverGroup.region
      String name = serverGroup.name

      List instances = serverGroup.instances
      Map asg = serverGroup.asg
      int minSize = asg.minSize

      if (!serverGroups.containsKey(region) || !serverGroups[region].contains(name) || minSize > instances.size()) {
        continue
      }

      seenServerGroup[name] = true
      def isComplete = hasSucceeded(instances)
      if (!isComplete) {
        return new DefaultTaskResult(PipelineStatus.RUNNING)
      }
    }
    if (seenServerGroup.values().contains(false)) {
      new DefaultTaskResult(PipelineStatus.RUNNING)
    } else {
      new DefaultTaskResult(PipelineStatus.SUCCEEDED)
    }
  }

}
