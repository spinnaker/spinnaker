/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.validator

import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class BasicOracleDeployDescriptionValidatorSpec extends Specification {

  @Shared BasicOracleDeployDescriptionValidator validator

  void setupSpec() {
    validator = new BasicOracleDeployDescriptionValidator()
  }

  void "invalid description fails validation"() {
    setup:
    def description = new BasicOracleDeployDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("region", "basicOracleDeployDescriptionValidator.region.empty")
    1 * errors.rejectValue("accountName", "basicOracleDeployDescriptionValidator.accountName.empty")
    1 * errors.rejectValue("application", "basicOracleDeployDescriptionValidator.application.empty")
    1 * errors.rejectValue("imageId", "basicOracleDeployDescriptionValidator.imageId.empty")
    1 * errors.rejectValue("shape", "basicOracleDeployDescriptionValidator.shape.empty")
  }

  void "pass validation with proper description inputs"() {
    setup:
    def description = new BasicOracleDeployDescription(
      imageId: "spinnaker-test-image",
      shape: "superBig",
      region: "us-phoenix-1",
      accountName: "myAcc",
      application: "spinnaker-test-v000"
    )

    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "invalid capacity"() {
    setup:
    def description = new BasicOracleDeployDescription(
      imageId: "spinnaker-test-image",
      shape: "superBig",
      region: "us-phoenix-1",
      accountName: "myAcc",
      application: "spinnaker-test-v000",
      targetSize: 2,
      capacity: new ServerGroup.Capacity(min: 3, max: 1)
    )

    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', 'basicOracleDeployDescriptionValidator.capacity.transposed', ['3', '1'], 'min size (3) > max size (1)')
  }

  void "invalid targetSize"() {
    setup:
    def description = new BasicOracleDeployDescription(
      imageId: "spinnaker-test-image",
      shape: "superBig",
      region: "us-phoenix-1",
      accountName: "myAcc",
      application: "spinnaker-test-v000",
      targetSize: -1
    )

    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('basicOracleDeployDescriptionValidator.targetSize', 'basicOracleDeployDescriptionValidator.targetSize.negative')
  }
}
