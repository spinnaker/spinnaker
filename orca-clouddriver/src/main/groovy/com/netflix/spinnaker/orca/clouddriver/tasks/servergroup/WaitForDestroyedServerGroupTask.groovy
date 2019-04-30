/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class WaitForDestroyedServerGroupTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = 10000
  long timeout = 1800000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)
    String serverGroupRegion = (stage.context.regions as Collection)?.getAt(0) ?: stage.context.region
    String serverGroupName = (stage.context.serverGroupName ?: stage.context.asgName) as String // TODO: Retire asgName
    Names names = Names.parseName(serverGroupName)
    String appName = stage.context.moniker?.app ?: names.app
    String clusterName = stage.context.moniker?.cluster ?: names.cluster
    try {
      def response = oortService.getCluster(appName, account, clusterName, cloudProvider)

      if (response.status != 200) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      Map cluster = objectMapper.readValue(response.body.in().text, Map)
      if (!cluster || !cluster.serverGroups) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([remainingInstances: []]).build()
      }
      def serverGroup = cluster.serverGroups.find { it.name == serverGroupName && it.region == serverGroupRegion }
      if (!serverGroup) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([remainingInstances: []]).build()
      }
      def instances = serverGroup.instances ?: []
      log.info("${serverGroupName}: not yet destroyed, found instances: ${instances?.join(', ') ?: 'none'}")
      return TaskResult.builder(ExecutionStatus.RUNNING).context([remainingInstances: instances.findResults { it.name }]).build()
    } catch (RetrofitError e) {
      def retrofitErrorResponse = new RetrofitExceptionHandler().handle(stage.name, e)
      if (e.response?.status == 404) {
        return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
      } else if (e.response?.status >= 500) {
        log.error("Unexpected retrofit error (${retrofitErrorResponse})")
        return TaskResult.builder(ExecutionStatus.RUNNING).context([lastRetrofitException: retrofitErrorResponse]).build()
      }

      throw e
    }
  }

}
