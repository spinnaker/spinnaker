/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oraclebmcs

import spock.lang.Specification


class OracleBMCSProviderSpec extends Specification {

  def oracleBMCSProvider

  def setup() {
    oracleBMCSProvider = new OracleBMCSCloudProvider()
  }

  def "Testing default values of OracleBMCSCloudProvider"() {
    expect:
    oracleBMCSProvider.id == "oraclebmcs"
  }
}
