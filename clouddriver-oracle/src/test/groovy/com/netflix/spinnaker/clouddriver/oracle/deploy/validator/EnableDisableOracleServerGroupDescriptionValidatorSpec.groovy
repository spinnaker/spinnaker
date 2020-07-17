/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.validator

import com.netflix.spinnaker.clouddriver.oracle.deploy.description.EnableDisableOracleServerGroupDescription
import spock.lang.Shared
import spock.lang.Specification

class EnableDisableOracleServerGroupDescriptionValidatorSpec extends Specification {

  @Shared
  EnableDisableOracleServerGroupDescriptionValidator validator

  void setupSpec() {
    validator = new EnableDisableOracleServerGroupDescriptionValidator()
  }

  void "invalid description fails validation"() {
    setup:
    def description = new EnableDisableOracleServerGroupDescription()
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("region", "enableDisableServerGroupDescription.region.empty")
    1 * errors.rejectValue("accountName", "enableDisableServerGroupDescription.accountName.empty")
  }

  void "valid description passes validation"() {
    setup:
    def description = new EnableDisableOracleServerGroupDescription(
      region: "us-phoenix-1",
      accountName: "DEFAULT"
    )
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }
}
