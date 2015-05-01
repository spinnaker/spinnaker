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

package com.netflix.spinnaker.orca.mine.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.mine.CanaryConfig
import com.netflix.spinnaker.orca.mine.CanaryDeployment
import com.netflix.spinnaker.orca.mine.Cluster
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.Recipient
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response

@Component
class RegisterCanaryTask implements Task {

  @Autowired
  MineService mineService
  @Autowired
  ObjectMapper objectMapper
  @Autowired
  ResultSerializationHelper resultSerializationHelper

  @Override
  TaskResult execute(Stage stage) {
    String app = stage.context.application ?: stage.execution.application
    Canary c = buildCanary(app, stage)
    Response response = mineService.registerCanary(c)
    String canaryId
    if (response.status == 200 && response.body.mimeType().startsWith('text/plain')) {
      canaryId = response.body.in().text
    } else {
      throw new IllegalStateException("Unable to handle $response")
    }
    Canary canary = mineService.getCanary(canaryId)
    return resultSerializationHelper.result(ExecutionStatus.SUCCEEDED, [canary: canary])
  }

  Canary buildCanary(String app, Stage stage) {
    Canary c = new Canary()
    c.application = app
    def context = stage.context
    c.owner = objectMapper.convertValue(context.owner, Recipient)
    c.watchers = objectMapper.convertValue(context.watchers, new TypeReference<List<Recipient>>() {})
    c.canaryConfig = objectMapper.convertValue(context.canaryConfig, CanaryConfig)
    c.canaryConfig.name = c.canaryConfig.name ?: stage.execution.id
    c.canaryConfig.application = app
    c.canaryDeployments =  stage.context.deployedClusterPairs.findAll { it.canaryStage == stage.id }.collect { Map pair ->
      def asCluster = { Map cluster ->
        new Cluster(name: cluster.clusterName, accountName: cluster.account, region: cluster.region, imageId: cluster.imageId, buildId: cluster.buildNumber)
      }
      new CanaryDeployment(canaryCluster: asCluster(pair.canary), baselineCluster: asCluster(pair.baseline))
    }

    return c
  }
}
