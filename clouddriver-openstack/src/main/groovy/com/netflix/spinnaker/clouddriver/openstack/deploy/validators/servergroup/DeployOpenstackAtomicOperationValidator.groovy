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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

//TODO this needs to be tested
@OpenstackOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component
class DeployOpenstackAtomicOperationValidator extends DescriptionValidator<DeployOpenstackAtomicOperationDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, DeployOpenstackAtomicOperationDescription description, Errors errors) {
    def validator = new OpenstackAttributeValidator("deployOpenstackAtomicOperationDescription", errors)

    if (!validator.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }
    validator.validateApplication(description.application, "application")
    validator.validateStack(description.stack, "stack")
    validator.validateNotEmpty(description.region, "region")
    validator.validateDetails(description.freeFormDetails, "details")
    validator.validateNonNegative(description.timeoutMins, "timeoutMins")
    validateServerGroup(validator, description.serverGroupParameters)
  }

  def validateServerGroup(OpenstackAttributeValidator validator, ServerGroupParameters parameters) {
    String prefix = "serverGroupParameters"
    parameters.with {
      validator.validateNotEmpty(instanceType, "${prefix}.instanceType")
      validator.validateNotEmpty(image, "${prefix}.image")
      validator.validateNotNull(maxSize, "${prefix}.maxSize")
      validator.validatePositive(maxSize, "${prefix}.maxSize")
      validator.validateNotNull(minSize, "${prefix}.maxSize")
      validator.validatePositive(minSize, "${prefix}.minSize")
      validator.validateGreaterThan(maxSize, minSize, "${prefix}.maxSize")
      validator.validateNotEmpty(networkId, "${prefix}.networkId")
      validator.validateNotEmpty(poolId, "${prefix}.poolId")
      validator.validateNotEmpty(securityGroups, "${prefix}.securityGroups")
    }
  }
}
