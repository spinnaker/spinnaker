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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupUpserter
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.SecurityGroupIngress

@Component
class GoogleSecurityGroupUpserter implements SecurityGroupUpserter, CloudProviderAware {

  final String cloudProvider = "gce"

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  SecurityGroupUpserter.OperationContext getOperationContext(Stage stage) {
    def ops = [[(SecurityGroupUpserter.OPERATION): stage.context]]

    def targets = [
      new MortService.SecurityGroup(name: stage.context.securityGroupName,
                                    region: stage.context.region,
                                    accountName: getCredentials(stage))
    ]

    return new SecurityGroupUpserter.OperationContext(ops, [targets: targets])
  }

  boolean isSecurityGroupUpserted(MortService.SecurityGroup upsertedSecurityGroup, Stage stage) {
    try {
      MortService.SecurityGroup existingSecurityGroup = mortService.getSecurityGroup(upsertedSecurityGroup.accountName,
                                                                                     cloudProvider,
                                                                                     upsertedSecurityGroup.name,
                                                                                     upsertedSecurityGroup.region)

      // Short-circuit the comparison logic if we can't retrieve the cached security group.
      if (!existingSecurityGroup) {
        return false
      }

      // What was upserted?
      Set<String> targetSecurityGroupSourceRanges = stage.context.sourceRanges as Set

      // What is currently cached?
      Set<String> existingSourceRanges = existingSecurityGroup.inboundRules.findResults { inboundRule ->
        inboundRule.range?.ip ? inboundRule.range.ip + inboundRule.range.cidr : null
      }.unique() as Set

      boolean rangesMatch = existingSourceRanges == targetSecurityGroupSourceRanges

      // Short-circuit the comparison logic if the ranges don't match.
      if (!rangesMatch) {
        return false
      }

      // What was upserted?
      Set<SecurityGroupIngress> targetSecurityGroupIngress =
        Arrays.asList(stage.mapTo("/ipIngress", MortService.SecurityGroup.SecurityGroupIngress[])) as Set

      // What is currently cached?
      def existingSecurityGroupIngressList = existingSecurityGroup.inboundRules.collect { inboundRule ->
        // Some protocols don't support port ranges.
        def portRanges = inboundRule.portRanges ?: [[startPort: null, endPort: null]]

        portRanges.collect {
          [
            startPort: it.startPort,
            endPort: it.endPort,
            type: inboundRule.protocol
          ]
        }
      }.flatten().unique()
      Set<SecurityGroupIngress> existingSecurityGroupIngress =
        existingSecurityGroupIngressList
        ? objectMapper.convertValue(existingSecurityGroupIngressList, SecurityGroupIngress[]) as Set
        : [] as Set

      boolean ingressMatches = existingSecurityGroupIngress == targetSecurityGroupIngress

      return ingressMatches
    } catch (RetrofitError e) {
      if (e.response?.status != 404) {
        throw e
      }
    }

    return false
  }
}
