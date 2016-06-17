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

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.CloneOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@OpenstackOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component("cloneOpenstackAtomicOperationValidator")
class CloneOpenstackAtomicOperationValidator extends DescriptionValidator<CloneOpenstackAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, CloneOpenstackAtomicOperationDescription description, Errors errors) {
    def helper = new OpenstackAttributeValidator("cloneOpenstackAtomicOperationDescription", errors)

    if (!helper.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }

    OpenstackCredentials credentials = (OpenstackCredentials) accountCredentialsProvider.getCredentials(description.account).credentials

    helper.validateServerGroupCloneSource(description.source, "source")

    if (description.application) {
      helper.validateApplication(description.application, "application")
    }

    if (description.stack) {
      helper.validateStack(description.stack, "stack")
    }

    if (description.freeFormDetails) {
      helper.validateDetails(description.freeFormDetails, "details")
    }

    if (description.region) {
      helper.validateNotEmpty(description.region, "region")
    }

    if (description.heatTemplate) {
      helper.validateHeatTemplate(description.heatTemplate, "heatTemplate", accountCredentialsProvider, description.account)
    }

    if (description.timeoutMins) {
      helper.validateNonNegative(description.timeoutMins, "timeoutMins")
    }

  }
}
