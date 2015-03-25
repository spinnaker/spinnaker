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

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class WaitForDestroyedAsgTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = 1800000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    String account = stage.context."account.name"
    if (stage.context.account && !account) {
      account = stage.context.account
    } else if (stage.context.credentials && !account) {
      account = stage.context.credentials
    }

    def serverGroupRegion = (stage.context.regions as Collection)?.getAt(0)
    def serverGroup = stage.context.asgName as String
    Names names = Names.parseName(serverGroup)
    try {
      def response = oortService.getCluster(names.app, account, names.cluster, stage.context.providerType ?: "aws")

      if (response.status != 200) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      }
      def cluster = objectMapper.readValue(response.body.in().text, Map)
      if (!cluster || !cluster.serverGroups) {
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      }
      if (!cluster.serverGroups.find { it.name == serverGroup && it.region == serverGroupRegion }) {
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      }

      return new DefaultTaskResult(ExecutionStatus.RUNNING)
    } catch (RetrofitError e) {
      def retrofitErrorResponse = new RetrofitExceptionHandler().handle(stage.name, e)
      if (e.response.status == 404) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      } else if (e.response.status >= 500) {
        log.error("Unexpected retrofit error (${retrofitErrorResponse})")
        return new DefaultTaskResult(ExecutionStatus.RUNNING, [lastRetrofitException: retrofitErrorResponse])
      }

      throw e
    }
  }

}
