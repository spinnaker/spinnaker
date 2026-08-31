/*
 * Copyright 2026 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.instance.ops.validators

import com.netflix.spinnaker.clouddriver.azure.AzureOperation
import com.netflix.spinnaker.clouddriver.azure.common.StandardAzureAttributeValidator
import com.netflix.spinnaker.clouddriver.azure.resources.instance.model.AzureInstanceDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@AzureOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("terminateAzureInstancesDescriptionValidator")
class TerminateAzureInstancesDescriptionValidator extends DescriptionValidator<AzureInstanceDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, AzureInstanceDescription description, ValidationErrors errors) {
    def helper = new StandardAzureAttributeValidator("azureInstanceDescription", errors)

    helper.validateCredentials(description.credentials, accountCredentialsProvider)
    helper.validateRegion(description.region)
    helper.validateName(description.targetServerGroupName, "serverGroupName")
    helper.validateNameList(description.targetInstanceIds, "instanceId")
  }
}
