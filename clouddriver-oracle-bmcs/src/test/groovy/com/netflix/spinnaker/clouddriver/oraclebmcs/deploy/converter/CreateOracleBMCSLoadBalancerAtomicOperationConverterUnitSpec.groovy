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
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.CreateLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.op.CreateOracleBMCSLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class CreateOracleBMCSLoadBalancerAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  CreateOracleBMCSLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CreateOracleBMCSLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(OracleBMCSNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  def "return correct description and operation"() {
    setup:
    def input = [application: "foo",
                 region     : "us-phoenix-1",
                 accountName: "my-obmcs-acc",
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
    description instanceof CreateLoadBalancerDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof CreateOracleBMCSLoadBalancerAtomicOperation
  }
}
