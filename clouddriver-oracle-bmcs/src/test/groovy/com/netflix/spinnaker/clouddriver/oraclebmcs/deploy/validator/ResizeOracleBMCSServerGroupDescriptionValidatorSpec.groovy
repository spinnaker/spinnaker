/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.validator

import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.ResizeOracleBMCSServerGroupDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class ResizeOracleBMCSServerGroupDescriptionValidatorSpec extends Specification {

  @Shared ResizeOracleBMCSServerGroupDescriptionValidator validator

  void setupSpec() {
    validator = new ResizeOracleBMCSServerGroupDescriptionValidator()
  }

  void "invalid description fails validation"() {
    setup:
    def description = new ResizeOracleBMCSServerGroupDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("serverGroupName", "resizeServerGroupDescription.serverGroupName.empty")
    1 * errors.rejectValue("region", "resizeServerGroupDescription.region.empty")
    1 * errors.rejectValue("accountName", "resizeServerGroupDescription.accountName.empty")
  }

  void "pass validation with proper description inputs"() {
    setup:
    def description = new ResizeOracleBMCSServerGroupDescription(
      serverGroupName: "spinnaker-test-v000",
      region: "us-phoenix-1",
      accountName: "DEFAULT"
    )

    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }
}

