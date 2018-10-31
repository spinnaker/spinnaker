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
    if (description.loadBalancerId) {
      // If a serverGroup is created with LoadBalancer, then a backendSet is created from the serverGroup with the same name.
      // The backendSet name is limited to 32 chars
      // This combineAppStackDetail (appName-stack-detail) is limited to 32-5 = 27 chars
      validateLimit(errors, combineAppStackDetail(description.application, description.stack, description.freeFormDetails), 27, "combineAppStackDetail")
      validateNotEmptyString(errors, description.backendSetName, "backendSetName")
    }
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

  /*
   * See NameBuilder.combineAppStackDetail. BasicOracleDeployHandler uses this to create "clusterName" and serverGroupName.
   * serverGroupName = appName-stack-detail-v001 or String.format("%s-v%03d", groupName, sequence)
   */
  static String combineAppStackDetail(String appName, String stack, String detail) {
    stack = stack != null ? stack : "";
    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }
    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }
    return appName;
  }
}
