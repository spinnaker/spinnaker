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

package com.netflix.spinnaker.orca.kato.tasks.securitygroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import static com.netflix.spinnaker.orca.mort.MortService.SecurityGroup.*

@Component
@CompileStatic
class WaitForUpsertedSecurityGroupTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = 600000

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    def status = ExecutionStatus.SUCCEEDED
    def context = stage.mapTo(StageData)
    context.targets.each { Map<String, Object> target ->
      try {
        def existingSecurityGroup = mortService.getSecurityGroup(
          target.credentials as String, 'aws', target.name as String, target.region as String, target.vpcId as String
        )

        def securityGroupIngress = applyMappings(
          target.securityGroupMappings as Map<String, String>,
          filterForSecurityGroupIngress(mortService, existingSecurityGroup)
        )
        if ((securityGroupIngress as Set) != (context.securityGroupIngress as Set)) {
          status = ExecutionStatus.RUNNING
        }
      } catch (RetrofitError e) {
        if (e.response?.status == 404) {
          status = ExecutionStatus.RUNNING
          return
        }

        throw e
      }
    }

    return new DefaultTaskResult(status)
  }

  static class StageData {
    Collection<Map<String, Object>> targets
    Collection<SecurityGroupIngress> securityGroupIngress
  }
}
