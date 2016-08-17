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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters.Scaler
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.AbstractOpenstackDescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator

/**
 * This class adds validation for creating and cloning server groups.
 */
abstract class AbstractServergroupOpenstackAtomicOperationValidator<T extends OpenstackAtomicOperationDescription> extends AbstractOpenstackDescriptionValidator<T> {

  final String prefix = "serverGroupParameters"
  final String scaleupPrefix = "scaleup"
  final String scaledownPrefix = "scaledown"

  /**
   * Validate server group parameters.
   * @param validator
   * @param parameters
   * @return
   */
  def validateServerGroup(OpenstackAttributeValidator validator, ServerGroupParameters parameters) {
    parameters.with {
      validator.validateNotEmpty(instanceType, "${prefix}.instanceType")
      validator.validateNotEmpty(image, "${prefix}.image")
      validator.validatePositive(maxSize, "${prefix}.maxSize")
      validator.validatePositive(minSize, "${prefix}.minSize")
      validator.validateGreaterThanEqual(maxSize, minSize, "${prefix}.maxSize")
      validator.validatePositive(desiredSize, "${prefix}.desiredSize")
      validator.validateGreaterThanEqual(desiredSize, minSize, "${prefix}.desiredSize")
      validator.validateNotEmpty(subnetId, "${prefix}.subnetId")
      validator.validateNotEmpty(loadBalancers, "${prefix}.loadBalancers")
      validator.validateNotEmpty(securityGroups, "${prefix}.securityGroups")
      int maxAdjustment = (maxSize && minSize) ? maxSize - minSize : 0
      [(scaleupPrefix):scaleup, (scaledownPrefix):scaledown].each { e -> validateScaler(validator, maxAdjustment, e.key, e.value) }
    }
  }

  def validateScaler(OpenstackAttributeValidator validator, int maxAdjustment, String type, Scaler scaler) {
    scaler?.with {
      if (adjustment) {
        validator.validateLessThanEqual(Math.abs(adjustment), maxAdjustment, "${prefix}.${type}.adjustment")
        if (scaleupPrefix == type) validator.validateGreaterThanEqual(adjustment, 0, "${prefix}.${type}.adjustment")
        if (scaledownPrefix == type) validator.validateLessThanEqual(adjustment, 0, "${prefix}.${type}.adjustment")
      }
      if (cooldown) validator.validatePositive(cooldown, "${prefix}.${type}.cooldown")
      if (period) validator.validatePositive(period, "${prefix}.${type}.period")
      if (threshold) validator.validatePositive(threshold, "${prefix}.${type}.threshold")
    }
  }

}
