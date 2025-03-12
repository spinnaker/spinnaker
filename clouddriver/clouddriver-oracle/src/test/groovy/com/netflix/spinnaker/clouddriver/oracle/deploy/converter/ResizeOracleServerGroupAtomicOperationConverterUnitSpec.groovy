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
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.ResizeOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.ResizeOracleServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class ResizeOracleServerGroupAtomicOperationConverterUnitSpec extends Specification {

  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final TARGET_SIZE = 5
  private static final REGION = "us-central1"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  void "resizeOracleServerGroupDescription type returns ResizeOracleServerGroupDescription and ResizeOracleServerGroupAtomicOperation"() {
    setup:
    def input = [serverGroupName: SERVER_GROUP_NAME,
                 targetSize     : TARGET_SIZE,
                 region         : REGION,
                 zone           : ZONE,
                 accountName    : ACCOUNT_NAME]

    ResizeOracleServerGroupAtomicOperationConverter converter =
      new ResizeOracleServerGroupAtomicOperationConverter(objectMapper: mapper)

    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(OracleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof ResizeOracleServerGroupDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof ResizeOracleServerGroupAtomicOperation
  }

}
