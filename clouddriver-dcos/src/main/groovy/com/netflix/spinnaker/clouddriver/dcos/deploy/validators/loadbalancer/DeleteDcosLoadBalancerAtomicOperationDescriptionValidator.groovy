/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@DcosOperation(AtomicOperations.DELETE_LOAD_BALANCER)
class DeleteDcosLoadBalancerAtomicOperationDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<DeleteDcosLoadBalancerAtomicOperationDescription> {

  @Autowired
  DeleteDcosLoadBalancerAtomicOperationDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "deleteDcosLoadBalancerAtomicOperationDescription")
  }

  @Override
  void validate(List priorDescriptions, DeleteDcosLoadBalancerAtomicOperationDescription description, ValidationErrors errors) {
    super.validate(priorDescriptions, description, errors)

    // TODO Regex name validation for DC/OS apps
    // Will need to apply to group as well.
    if (!description.loadBalancerName || description.loadBalancerName.empty) {
      errors.rejectValue("loadBalancerName", "${descriptionName}.loadBalancerName.empty");
    } else if (!MarathonPathId.isPartValid(description.loadBalancerName)) {
      errors.rejectValue "loadBalancerName", "${descriptionName}.loadBalancerName.invalid"
    }
  }
}
