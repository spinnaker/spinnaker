/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

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
import retrofit.RetrofitError

@Slf4j
abstract class AbstractWaitForAppEngineServerGroupStopStartTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = 10000
  long timeout = 1800000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  abstract boolean isStart()

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)
    String serverGroupName = (stage.context.serverGroupName ?: stage.context.asgName) as String
    Names names = Names.parseName(serverGroupName)
    String appName = stage.context.moniker?.app ?: names.app
    String clusterName = stage.context.moniker?.cluster ?: names.cluster
    try {
      def response = oortService.getCluster(appName, account, clusterName, cloudProvider)
      Map cluster = objectMapper.readValue(response.body.in().text, Map)

      def serverGroup = cluster.serverGroups.find { it.name == serverGroupName }
      if (!serverGroup) {
        log.info("${serverGroupName}: not found.")
        return new TaskResult(ExecutionStatus.TERMINAL)
      }

      def desiredServingStatus = start ? "SERVING" : "STOPPED"
      if (serverGroup.servingStatus == desiredServingStatus) {
        return new TaskResult(ExecutionStatus.SUCCEEDED)
      } else {
        log.info("${serverGroupName}: not yet ${start ? "started" : "stopped"}.")
        return new TaskResult(ExecutionStatus.RUNNING)
      }

    } catch (RetrofitError e) {
      def retrofitErrorResponse = new RetrofitExceptionHandler().handle(stage.name, e)
      if (e.response?.status == 404) {
        return new TaskResult(ExecutionStatus.TERMINAL)
      } else if (e.response?.status >= 500) {
        log.error("Unexpected retrofit error (${retrofitErrorResponse})")
        return new TaskResult(ExecutionStatus.RUNNING, [lastRetrofitException: retrofitErrorResponse])
      }

      throw e
    }
  }
}
