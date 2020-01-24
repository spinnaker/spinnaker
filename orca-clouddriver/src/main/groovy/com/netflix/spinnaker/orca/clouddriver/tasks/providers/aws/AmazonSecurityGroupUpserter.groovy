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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupUpserter
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.filterForSecurityGroupIngress

@Component
class AmazonSecurityGroupUpserter implements SecurityGroupUpserter, CloudProviderAware {

  final String cloudProvider = "aws"

  @Autowired
  MortService mortService

  @Override
  SecurityGroupUpserter.OperationContext getOperationContext(Stage stage) {
    def operation = new HashMap(stage.context)
    operation.regions = operation.regions ?: (operation.region ? [operation.region] : [])

    if (!operation.regions) {
      throw new IllegalStateException("Must supply at least one region")
    }

    def allVPCs = mortService.getVPCs()

    def ops = operation.regions.collect { String region ->
      def vpcId = null
      if (operation.vpcId) {
        vpcId = MortService.VPC.findForRegionAndAccount(
            allVPCs, operation.vpcId as String, region, operation.credentials as String
        ).id
      }
      /**
       * Additional security group details can be used to pass additional details that can be used in custom preprocessor in clouddriver,
       * In order to consume this detail, changes are required in clouddriver preprocessor/atomic operation.
       */
      return [
          (SecurityGroupUpserter.OPERATION): [
              name                : operation.securityGroupName,
              credentials         : getCredentials(stage),
              region              : region,
              vpcId               : vpcId,
              description         : operation.description,
              securityGroupIngress: operation.securityGroupIngress,
              ipIngress           : operation.ipIngress,
              ingressAppendOnly   : operation.ingressAppendOnly ?: false,
              additionalSecurityGroupDetails : operation.additionalSecurityGroupDetails
          ]
      ]
    }

    def targets = ops.collect {
      return new MortService.SecurityGroup(name: it[OPERATION].name,
                                           region: it[OPERATION].region,
                                           accountName: it[OPERATION].credentials,
                                           vpcId: it[OPERATION].vpcId)
    }

    def securityGroupIngress = stage.context.securityGroupIngress ?: []

    return new SecurityGroupUpserter.OperationContext(ops, [targets: targets, securityGroupIngress: securityGroupIngress])
  }

  boolean isSecurityGroupUpserted(MortService.SecurityGroup upsertedSecurityGroup, Stage stage) {
    if (!upsertedSecurityGroup) {
      return false
    }

    try {
      MortService.SecurityGroup existingSecurityGroup = mortService.getSecurityGroup(upsertedSecurityGroup.accountName,
                                                                                     cloudProvider,
                                                                                     upsertedSecurityGroup.name,
                                                                                     upsertedSecurityGroup.region,
                                                                                     upsertedSecurityGroup.vpcId)

      Set mortSecurityGroupIngress = filterForSecurityGroupIngress(mortService, existingSecurityGroup) as Set
      Set targetSecurityGroupIngress = Arrays.asList(stage.mapTo("/securityGroupIngress",
                                                                 MortService.SecurityGroup.SecurityGroupIngress[]))
      if (stage.context.ingressAppendOnly) {
        return mortSecurityGroupIngress.containsAll(targetSecurityGroupIngress)
      }
      return mortSecurityGroupIngress == targetSecurityGroupIngress
    } catch (RetrofitError e) {
      if (e.response?.status != 404) {
        throw e
      }
    }
    return false
  }
}
