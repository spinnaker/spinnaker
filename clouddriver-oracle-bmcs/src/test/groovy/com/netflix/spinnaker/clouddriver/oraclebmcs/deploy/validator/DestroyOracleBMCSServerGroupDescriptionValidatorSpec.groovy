/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.validator

import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.DestroyOracleBMCSServerGroupDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class DestroyOracleBMCSServerGroupDescriptionValidatorSpec extends Specification {

  @Shared
  DestroyOracleBMCSServerGroupDescriptionValidator validator

  void setupSpec() {
    validator = new DestroyOracleBMCSServerGroupDescriptionValidator()
  }

  void "invalid description fails validation"() {
    setup:
    def description = new DestroyOracleBMCSServerGroupDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("accountName", "destroyServerGroupDescription.accountName.empty")
    1 * errors.rejectValue("region", "destroyServerGroupDescription.region.empty")
    1 * errors.rejectValue("serverGroupName", "destroyServerGroupDescription.serverGroupName.empty")
  }

  void "valid description passes validation"() {
    setup:
    def description = new DestroyOracleBMCSServerGroupDescription(
      accountName: "DEFAULT",
      region: "us-phoenix-1",
      serverGroupName: "my-group-01"
    )

    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }
}
