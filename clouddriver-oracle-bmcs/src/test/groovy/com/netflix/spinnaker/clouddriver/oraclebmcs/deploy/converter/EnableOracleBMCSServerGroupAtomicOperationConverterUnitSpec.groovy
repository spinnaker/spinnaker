/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.EnableDisableOracleBMCSServerGroupDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.op.EnableOracleBMCSServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class EnableOracleBMCSServerGroupAtomicOperationConverterUnitSpec extends Specification {

  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"

  private static final REGION = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  EnableOracleBMCSServerGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new EnableOracleBMCSServerGroupAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(OracleBMCSNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "enableOracleBMCSServerGroupDescription type returns EnableDisableOracleBMCSServerGroupDescription and EnableOracleBMCSServerGroupAtomicOperation"() {
    setup:
    def input = [serverGroupName: SERVER_GROUP_NAME,
                 region         : REGION,
                 accountName    : ACCOUNT_NAME]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof EnableDisableOracleBMCSServerGroupDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof EnableOracleBMCSServerGroupAtomicOperation
  }
}
