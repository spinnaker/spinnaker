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

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.OpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.Errors

/**
 * This class serves as base class for all openstack atomic operation validators.
 * It validates region and account information, which are common to all atomic operations.
 * @param <T>
 */
abstract class AbstractOpenstackDescriptionValidator<T extends OpenstackAtomicOperationDescription> extends DescriptionValidator<T> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, T description, Errors errors) {
    def validator = new OpenstackAttributeValidator(context, errors)
    if (!validator.validateCredentials(description.account, accountCredentialsProvider)) {
      return
    }
    if (!validator.validateRegion(description.region, description.credentials.provider)) {
      return
    }
    validate(validator, priorDescriptions, description, errors)
  }

  /**
   * Subclasses will implement this to provide operation-specific validation
   * @param validator
   * @param priorDescriptions
   * @param description
   * @param errors
   */
  abstract void validate(OpenstackAttributeValidator validator, List priorDescriptions, T description, Errors errors)

  /**
   * String description of this validation
   * @return
   */
  abstract String getContext()
}
