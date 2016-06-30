/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.OpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.AbstractOpenstackDescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters

/**
 * This class adds validation for creating and cloning server groups.
 */
abstract class AbstractServergroupOpenstackAtomicOperationValidator<T extends OpenstackAtomicOperationDescription> extends AbstractOpenstackDescriptionValidator<T> {

  /**
   * Validate server group parameters.
   * @param validator
   * @param parameters
   * @return
   */
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
