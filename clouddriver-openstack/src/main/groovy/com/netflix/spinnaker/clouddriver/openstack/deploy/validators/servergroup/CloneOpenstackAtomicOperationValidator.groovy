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

import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.CloneOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@OpenstackOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component
class CloneOpenstackAtomicOperationValidator extends AbstractServergroupOpenstackAtomicOperationValidator<CloneOpenstackAtomicOperationDescription> {

  String context = "cloneOpenstackAtomicOperationDescription"

  @Override
  void validate(OpenstackAttributeValidator validator, List priorDescriptions, CloneOpenstackAtomicOperationDescription description, Errors errors) {

    if (!validator.validateNotNull(description.source, "source")) {
      return
    }
    validator.validateNotEmpty(description.source.serverGroupName, "serverGroupName")
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

}
