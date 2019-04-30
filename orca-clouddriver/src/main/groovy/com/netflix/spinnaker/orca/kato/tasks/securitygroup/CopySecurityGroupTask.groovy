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

package com.netflix.spinnaker.orca.kato.tasks.securitygroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.applyMappings
import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.filterForSecurityGroupIngress
import static com.netflix.spinnaker.orca.clouddriver.MortService.VPC.findForRegionAndAccount

@Component
class CopySecurityGroupTask implements Task {
  @Autowired
  KatoService kato

  @Autowired
  MortService mortService

  @Override
  TaskResult execute(Stage stage) {
    def operation = stage.mapTo(StageData)
    def currentSecurityGroup = mortService.getSecurityGroup(
      operation.credentials, operation.provider, operation.name, operation.region, operation.vpcId
    )

    def allVPCs = mortService.getVPCs()

    def securityGroupIngress = filterForSecurityGroupIngress(mortService, currentSecurityGroup)
    def operations = operation.targets.collect {
      [
        upsertSecurityGroupDescription: [
          name                : operation.name,
          credentials         : it.credentials,
          region              : it.region,
          vpcId               : it.vpcId ? findForRegionAndAccount(allVPCs, it.vpcId, it.region, it.credentials).id : null,
          description         : currentSecurityGroup.description,
          securityGroupIngress: applyMappings(it.securityGroupMappings, securityGroupIngress)
        ]
      ]
    }

    def taskId = kato.requestOperations(operations).toBlocking().first()
    Map outputs = [
      "notification.type"   : "upsertsecuritygroup",
      "kato.last.task.id"   : taskId,
      "securityGroupIngress": securityGroupIngress,
      "targets"             : operations.collect {
        [
          credentials: it.upsertSecurityGroupDescription.credentials,
          region     : it.upsertSecurityGroupDescription.region,
          vpcId      : it.upsertSecurityGroupDescription.vpcId,
          name       : it.upsertSecurityGroupDescription.name,
          description: it.upsertSecurityGroupDescription.description
        ]
      }
    ]
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }

  static class StageData {
    String credentials
    String provider = "aws"
    String name
    String region
    String vpcId
    Collection<Target> targets

    static class Target {
      String credentials
      String region
      String vpcId
      String name
      Map<String, String> securityGroupMappings
    }
  }
}
