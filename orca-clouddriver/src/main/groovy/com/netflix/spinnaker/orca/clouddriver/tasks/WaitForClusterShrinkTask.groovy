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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WaitForClusterShrinkTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = 10000
  long timeout = 1800000

  @Autowired
  OortHelper oortHelper

  @Override
  TaskResult execute(Stage stage) {

    List<Map> waitingOnDestroy = stage.context.waitingOnDestroy

    if (waitingOnDestroy == null) {
      waitingOnDestroy = []
      Map<String, List<String>> dsg = stage.context.'deploy.server.groups' as Map

      if (dsg) {
        dsg.each { region, groups ->
          groups.each { waitingOnDestroy << [region: region, name: it] }
        }
      }
    }

    if (!waitingOnDestroy) {
      return DefaultTaskResult.SUCCEEDED
    }

    def config = stage.mapTo(ShrinkClusterTask.ShrinkConfig)
    def names = Names.parseName(config.cluster)

    Optional<Map> cluster = oortHelper.getCluster(names.app, config.credentials, config.cluster, config.cloudProvider)
    if (!cluster.isPresent()) {
      return DefaultTaskResult.SUCCEEDED
    }

    def serverGroups = cluster.get().serverGroups

    if (!serverGroups) {
      return DefaultTaskResult.SUCCEEDED
    }

    def remaining = waitingOnDestroy.findAll { wait ->
      serverGroups.any { it.region == wait.region && it.name == wait.name }
    }

    if (remaining) {
      return new DefaultTaskResult(ExecutionStatus.RUNNING, [waitingOnDestroy: remaining])
    }

    return DefaultTaskResult.SUCCEEDED
  }
}
