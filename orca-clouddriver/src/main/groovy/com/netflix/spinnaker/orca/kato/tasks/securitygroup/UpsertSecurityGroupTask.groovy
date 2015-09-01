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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.mort.MortService.VPC.*

@Component
class UpsertSecurityGroupTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def operation = convert(stage)
    operation.regions = operation.regions ?: (operation.region ? [operation.region] : [])

    if (!operation.regions) {
      throw new IllegalStateException("Must supply at least one region")
    }

    def allVPCs = mortService.getVPCs()

    def operations = operation.regions.collect { String region ->
      def vpcId = null
      if (operation.vpcId) {
        vpcId = findForRegionAndAccount(
          allVPCs, operation.vpcId as String, region, operation.credentials as String
        ).id
      }

      return [
        upsertSecurityGroupDescription: [
          name                : operation.name,
          credentials         : operation.credentials,
          region              : region,
          vpcId               : vpcId,
          description         : operation.description,
          securityGroupIngress: operation.securityGroupIngress
        ]
      ]
    }

    def taskId = kato.requestOperations(operations).toBlocking().first()

    Map outputs = [
      "notification.type"   : "upsertsecuritygroup",
      "kato.last.task.id"   : taskId,
      "targets"             : operations.collect {
        [
          credentials: it.upsertSecurityGroupDescription.credentials,
          region     : it.upsertSecurityGroupDescription.region,
          vpcId      : it.upsertSecurityGroupDescription.vpcId,
          name       : it.upsertSecurityGroupDescription.name
        ]
      },
      "securityGroupIngress": stage.context.securityGroupIngress ?: []
    ]

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }

  Map convert(Stage stage) {
    mapper.convertValue(stage.context, Map)
  }
}
