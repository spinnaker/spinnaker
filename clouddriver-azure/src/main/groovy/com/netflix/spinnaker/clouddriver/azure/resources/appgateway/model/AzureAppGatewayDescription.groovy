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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model

import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class AzureAppGatewayDescription extends AzureResourceOpsDescription {
  String loadBalancerName
  String vnet
  String subnet
  String securityGroup
  String dnsName
  String cluster
  List<String> serverGroups
  List<AzureAppGatewayHealthcheckProbe> probes = []
  List<AzureAppGatewayRule> rules = []
  // TODO: remove hardcoded sku, tier and capacity
  //   We will come back and revisit this if we need to support different selection (we will also need deck changes to reflect that)
  String sku = "Standard_Small"
  String tier = "Standard"
  long capacity = 2

  static class AzureAppGatewayHealthcheckProbe {
    enum AzureLoadBalancerProbesType {
      HTTP
    }

    String name
    AzureLoadBalancerProbesType protocol = "HTTP"
    String host = "localhost"
    String path
    long interval = 120
    long timeout = 30
    long unhealthyThreshold = 8
  }

  static class AzureAppGatewayRule {
    enum AzureLoadBalancingRulesType {
      HTTP
      // TODO: add support for HTTPS port mappings
    }

    String name
    AzureLoadBalancingRulesType protocol
    long externalPort
    long backendPort
    String sslCertificate
  }

}
