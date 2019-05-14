/*
 * Copyright 2016 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.ops.converters.UpsertAzureLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class UpsertAzureAppGatewayAtomicOperationConverterSpec extends  Specification{

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared UpsertAzureLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertAzureLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(AzureNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "Create an AzureAppGatewayDescription from a given input"() {
    setup:
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    def input = [
      name: "testappgw-lb1-d1",
      loadBalancerName: "testappgw-lb1-d1",
      loadBalancerType: "Azure Application Gateway",
      region: "westus",
      accountName: "myazure-account",
      cloudProvider: "azure",
      appName: "testappgw",
      stack: "lb1",
      detail: "d1",
      probes: [
        [
          probeName: "healthcheck1",
          probeProtocol: "HTTP",
          probePath: "/healthcheck",
          probeInterval: 120,
          timeout: 30,
          unhealthyThreshold: 8
        ]
      ],
      loadBalancingRules: [
        [
          ruleName: "lbRule1",
          protocol: "HTTP",
          externalPort: 80,
          backendPort: 8080,
        ],
        [
          ruleName: "lbRule2",
          protocol: "HTTP",
          externalPort: 8080,
          backendPort: 8080,
        ]
      ]
    ]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof AzureAppGatewayDescription
    mapper.writeValueAsString(description).replace('\r', '') == expectedFullDescription
  }

  private static String expectedFullDescription = '''{
  "name" : "testappgw-lb1-d1",
  "cloudProvider" : "azure",
  "accountName" : "myazure-account",
  "appName" : "testappgw",
  "stack" : "lb1",
  "detail" : "d1",
  "credentials" : null,
  "region" : "westus",
  "user" : null,
  "createdTime" : null,
  "lastReadTime" : 0,
  "tags" : { },
  "loadBalancerName" : "testappgw-lb1-d1",
  "vnet" : null,
  "subnet" : null,
  "subnetResourceId" : null,
  "vnetResourceGroup" : null,
  "hasNewSubnet" : null,
  "useDefaultVnet" : false,
  "securityGroup" : null,
  "dnsName" : null,
  "cluster" : null,
  "serverGroups" : null,
  "trafficEnabledSG" : null,
  "publicIpName" : null,
  "probes" : [ {
    "probeName" : "healthcheck1",
    "probeProtocol" : "HTTP",
    "probePort" : "localhost",
    "probePath" : "/healthcheck",
    "probeInterval" : 120,
    "timeout" : 30,
    "unhealthyThreshold" : 8
  } ],
  "loadBalancingRules" : [ {
    "ruleName" : "lbRule1",
    "protocol" : "HTTP",
    "externalPort" : 80,
    "backendPort" : 8080,
    "sslCertificate" : null
  }, {
    "ruleName" : "lbRule2",
    "protocol" : "HTTP",
    "externalPort" : 8080,
    "backendPort" : 8080,
    "sslCertificate" : null
  } ],
  "sku" : "Standard_Small",
  "tier" : "Standard",
  "capacity" : 2
}'''
}
