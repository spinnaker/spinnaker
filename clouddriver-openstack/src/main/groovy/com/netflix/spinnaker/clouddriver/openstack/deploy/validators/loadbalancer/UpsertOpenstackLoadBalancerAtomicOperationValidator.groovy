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

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@OpenstackOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component
class UpsertOpenstackLoadBalancerAtomicOperationValidator extends DescriptionValidator<OpenstackLoadBalancerDescription> {
  static final String context = "upsertOpenstackLoadBalancerAtomicOperationDescription"

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, OpenstackLoadBalancerDescription description, Errors errors) {

    def validator = new OpenstackAttributeValidator(context, errors)

    if (!validator.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }

    if (description.id) {
      validator.validateUUID(description.id, 'id')
    } else {
      validator.validatePort(description.externalPort, 'externalPort')
      validator.validateUUID(description.subnetId, 'subnetId')
      validator.validateNotNull(description.protocol, 'protocol')
    }

    // Shared validations between create/update
    validator.validateNotEmpty(description.name, 'name')
    validator.validateNotEmpty(description.region, 'region')
    validator.validatePort(description.internalPort, 'internalPort')

    validator.validateNotNull(description.method, 'method')

    validateHealthMonitor(validator, description.healthMonitor)

    if (description.floatingIpId) {
      validator.validateUUID(description.floatingIpId, 'floatingIpId')
    }
  }

  /**
   * Helper method to validate load balancer
   * @param validator
   * @param healthMonitor
   */
  protected void validateHealthMonitor(OpenstackAttributeValidator validator, PoolHealthMonitor healthMonitor) {
    if (healthMonitor) {
      if (!healthMonitor.type) {
        validator.reject('type', 'type')
      }
      validator.validatePositive(healthMonitor.delay, 'delay')
      validator.validatePositive(healthMonitor.timeout, 'timeout')
      validator.validatePositive(healthMonitor.maxRetries, 'maxRetries')
      if (healthMonitor.httpMethod) {
        validator.validateHttpMethod(healthMonitor.httpMethod, 'httpMethod')
      }
      if (healthMonitor.expectedHttpStatusCodes) {
        healthMonitor.expectedHttpStatusCodes.each {
          validator.validateHttpStatusCode(it, 'expectedHttpStatusCodes')
        }
      }
      if (healthMonitor.url) {
        validator.validateURI(healthMonitor.url, 'url')
      }
    }
  }
}
