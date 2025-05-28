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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.azure

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup.SecurityGroupUpserter
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureSecurityGroupUpserter implements SecurityGroupUpserter, CloudProviderAware {

  final String cloudProvider = "azure"

  @Autowired
  MortService mortService

  @Override
  SecurityGroupUpserter.OperationContext getOperationContext(StageExecution stage) {
    def ops = [[(SecurityGroupUpserter.OPERATION): stage.context]]

    def targets = [
      new MortService.SecurityGroup(name: stage.context.securityGroupName,
        region: stage.context.region,
        accountName: getCredentials(stage))
    ]

    return new SecurityGroupUpserter.OperationContext(ops, [targets: targets])
  }

  boolean isSecurityGroupUpserted(MortService.SecurityGroup upsertedSecurityGroup, StageExecution _) {
    try {
      return Retrofit2SyncCall.execute(mortService.getSecurityGroup(upsertedSecurityGroup.accountName,
        cloudProvider,
        upsertedSecurityGroup.name,
        upsertedSecurityGroup.region))
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() != 404) {
        throw e
      }
    }
    return false
  }
}
