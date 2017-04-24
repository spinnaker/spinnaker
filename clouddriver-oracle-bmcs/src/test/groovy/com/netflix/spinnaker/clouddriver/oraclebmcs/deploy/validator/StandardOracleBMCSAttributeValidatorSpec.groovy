/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.validator

import org.springframework.validation.Errors
import spock.lang.Specification

class StandardOracleBMCSAttributeValidatorSpec extends Specification {

  void "validateNotEmptyString ok"() {
    setup:
    def errors = Mock(Errors)
    def validator = new StandardOracleBMCSAttributeValidator("context", errors)

    when:
    validator.validateNotEmptyString("DEFAULT", "accountName")
    then:
    0 * errors._

    when:
    validator.validateNotEmptyString("", "accountName")
    then:
    1 * errors._

    when:
    validator.validateNotEmptyString(null, "accountName")
    then:
    1 * errors._
  }
}
