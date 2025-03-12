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
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.UpsertOracleLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class UpsertOracleLoadBalancerAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertOracleLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertOracleLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(OracleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  def "return correct description and operation"() {
    setup:
    def input = [application: "foo",
                 region     : "us-phoenix-1",
                 accountName: "my-oracle" +
                   "-acc",
                 stack      : "bar",
                 shape      : "100Mbps",
                 policy     : "ROUND_ROBIN",
                 subnetIds  : ["1", "2"],
                 listener   : [
                   port    : 80,
                   protocol: "tcp"
                 ],
                 healthCheck: [
                   protocol         : "http",
                   port             : 8080,
                   interval         : 10,
                   retries          : 5,
                   timeout          : 5,
                   url              : "/healthz",
                   statusCode       : 200,
                   responseBodyRegex: ".*GOOD.*"
                 ]]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertLoadBalancerDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertOracleLoadBalancerAtomicOperation
  }
}
