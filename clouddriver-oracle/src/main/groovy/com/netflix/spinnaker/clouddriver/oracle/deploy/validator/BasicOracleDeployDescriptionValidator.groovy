/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.validator

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.oracle.OracleOperation
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@OracleOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("basicOracleDeployDescriptionValidator")
class BasicOracleDeployDescriptionValidator extends StandardOracleAttributeValidator<BasicOracleDeployDescription> {
  
  @Override
  void validate(List priorDescriptions, BasicOracleDeployDescription description, Errors errors) {
    context = "basicOracleDeployDescriptionValidator"
    validateNotEmptyString(errors, description.application, "application")
    validateNotEmptyString(errors, description.region, "region")
    validateNotEmptyString(errors, description.accountName, "accountName")
    validateNotEmptyString(errors, description.imageId, "imageId")
    validateNotEmptyString(errors, description.shape, "shape")
    //TODO: check serviceLimits?
    Integer targetSize = description.targetSize?: (description.capacity?.desired?:0)
    validateNonNegative(errors, targetSize?:0, "targetSize")
    if (description.capacity) {
      validateCapacity(errors, description.capacity.min, description.capacity.max, description.capacity.desired)
    }
  }
}
