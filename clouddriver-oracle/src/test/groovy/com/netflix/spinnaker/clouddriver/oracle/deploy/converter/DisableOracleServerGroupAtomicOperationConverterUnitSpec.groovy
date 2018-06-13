/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.EnableDisableOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.DisableOracleServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class DisableOracleServerGroupAtomicOperationConverterUnitSpec extends Specification {

  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"

  private static final REGION = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DisableOracleServerGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DisableOracleServerGroupAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(OracleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "disableOracleServerGroupDescription type returns EnableDisableOracleServerGroupDescription and DisableOracleServerGroupAtomicOperation"() {
    setup:
    def input = [serverGroupName: SERVER_GROUP_NAME,
                 region         : REGION,
                 accountName    : ACCOUNT_NAME]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof EnableDisableOracleServerGroupDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DisableOracleServerGroupAtomicOperation
  }
}
