/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.instance

import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.AbstractOpenstackDescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@OpenstackOperation(AtomicOperations.REBOOT_INSTANCES)
@Component
class RebootOpenstackInstancesDescriptionValidator extends AbstractOpenstackDescriptionValidator<OpenstackInstancesDescription> {

  String context = "rebootOpenstackRegistrationAtomicOperationDescription"

  @Override
  void validate(OpenstackAttributeValidator validator, List priorDescriptions, OpenstackInstancesDescription description, Errors errors) {
    validator.validateNotEmpty(description.instanceIds, "instanceIds")
  }
}
