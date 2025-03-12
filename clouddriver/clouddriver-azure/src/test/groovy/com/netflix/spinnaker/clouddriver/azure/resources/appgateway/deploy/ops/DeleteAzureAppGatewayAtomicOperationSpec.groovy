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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.deploy.ops

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.ops.converters.DeleteAzureAppGatewayAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.ops.DeleteAzureAppGatewayAtomicOperation
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class DeleteAzureAppGatewayAtomicOperationSpec extends Specification{

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared DeleteAzureAppGatewayAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeleteAzureAppGatewayAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(AzureNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "Create deleteAzureAppGatewayAtomicOperation object - simple test"() {
    setup:
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    def input = '''{ "cloudProvider" : "azure", "appName" : "testappgw", "loadBalancerName" : "testappgw-lb1-d1", "credentials" : "myazure-account", "region" : "westus", "name" : "testappgw-lb1-d1", "user" : "[anonymous]" }'''

    when:
    DeleteAzureAppGatewayAtomicOperation operation = converter.convertOperation(mapper.readValue(input, Map))
    AzureAppGatewayDescription description = converter.convertDescription(mapper.readValue(input, Map))

    then:
    operation
    mapper.writeValueAsString(description).replace('\r', '') == expectedFullDescription
  }

  private static String expectedFullDescription = '''{
  "name" : "testappgw-lb1-d1",
  "cloudProvider" : "azure",
  "accountName" : "myazure-account",
  "appName" : "testappgw",
  "stack" : null,
  "detail" : null,
  "credentials" : null,
  "region" : "westus",
  "user" : "[anonymous]",
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
  "probes" : [ ],
  "loadBalancingRules" : [ ],
  "sku" : "Standard_Small",
  "tier" : "Standard",
  "capacity" : 2
}'''
}
