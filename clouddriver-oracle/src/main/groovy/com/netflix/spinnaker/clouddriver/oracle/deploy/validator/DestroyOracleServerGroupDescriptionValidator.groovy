/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.validator

import com.netflix.spinnaker.clouddriver.oracle.OracleOperation
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DestroyOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@OracleOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyOracleServerGroupDescriptionValidator")
class DestroyOracleServerGroupDescriptionValidator extends StandardOracleAttributeValidator<DestroyOracleServerGroupDescription> {

  @Override
  void validate(List priorDescriptions, DestroyOracleServerGroupDescription description, Errors errors) {
    context = "destroyServerGroupDescription"
    validateNotEmptyString(errors, description.accountName, "accountName")
    validateNotEmptyString(errors, description.region, "region")
    validateNotEmptyString(errors, description.serverGroupName, "serverGroupName")
  }
}
