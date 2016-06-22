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
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters
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
    def validator = new OpenstackAttributeValidator("cloneOpenstackAtomicOperationDescription", errors)

    if (!validator.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }
    if (!validator.validateNotNull(description.source, "source")) {
      return
    }
    validator.validateNotEmpty(description.source.region, "region")
    validator.validateNotEmpty(description.source.serverGroup, "serverGroup")
    if (description.application) {
      validator.validateApplication(description.application, "application")
    }
    validator.validateStack(description.stack, "stack")
    validator.validateDetails(description.freeFormDetails, "details")
    if (description.serverGroupParameters) {
      validateServerGroup(validator, description.serverGroupParameters)
    }
    if (description.timeoutMins) {
      validator.validateNonNegative(description.timeoutMins, "timeoutMins")
    }
  }


  //TODO this is copy-paste from DeployOpenstackAtomicOperationValidator
  def validateServerGroup(OpenstackAttributeValidator validator, ServerGroupParameters parameters) {
    String prefix = "serverGroupParameters"
    parameters.with {
      validator.validateNotEmpty(instanceType, "${prefix}.instanceType")
      validator.validateNotEmpty(image, "${prefix}.image")
      validator.validatePositive(maxSize, "${prefix}.maxSize")
      validator.validatePositive(minSize, "${prefix}.minSize")
      validator.validateGreaterThan(maxSize, minSize, "${prefix}.maxSize")
      validator.validateNotEmpty(networkId, "${prefix}.networkId")
      validator.validateNotEmpty(poolId, "${prefix}.poolId")
      validator.validateNotEmpty(securityGroups, "${prefix}.securityGroups")
    }
  }

}
