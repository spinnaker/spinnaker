/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@OpenstackOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("deployOpenstackAtomicOperationValidator")
class DeployOpenstackAtomicOperationValidator extends DescriptionValidator<DeployOpenstackAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, DeployOpenstackAtomicOperationDescription description, Errors errors) {
    def validator = new OpenstackAttributeValidator("deployOpenstackAtomicOperationDescription", errors)

    validator.validateCredentials(description.account, accountCredentialsProvider)
    validator.validateApplication(description.application, "application")
    validator.validateStack(description.stack, "stack")
    validator.validateNotEmpty(description.region, "region")
    validator.validateDetails(description.freeFormDetails, "details")
    validator.validateHeatTemplate(description.heatTemplate, "heatTemplate", accountCredentialsProvider, description.account)
    validator.validateNonNegative(description.timeoutMins, "timeoutMins")

  }
}
