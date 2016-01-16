/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model

import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class AzureLoadBalancerDescription extends AzureResourceOpsDescription {
  String loadBalancerName
  String vnet
  String securityGroups
  String dnsName
  List<AzureLoadBalancerProbe> probes = []
  List<AzureLoadBalancingRule> loadBalancingRules = []
  List<AzureLoadBalancerInboundNATRule> inboundNATRules = []

  static class AzureLoadBalancerProbe {
    enum AzureLoadBalancerProbesType {
      HTTP, TCP
    }

    String probeName
    AzureLoadBalancerProbesType probeProtocol
    Integer probePort
    String probePath
    Integer probeInterval
    Integer unhealthyThreshold
  }

  static class AzureLoadBalancingRule {
    enum AzureLoadBalancingRulesType {
      TCP, UDP
    }

    String ruleName
    AzureLoadBalancingRulesType protocol
    Integer externalPort
    Integer backendPort
    String probeName
    String persistence
    Integer idleTimeout
  }

  static class AzureLoadBalancerInboundNATRule {
    enum AzureLoadBalancerInboundNATRulesProtocolType {
      HTTP, TCP
    }
    enum AzureLoadBalancerInboundNATRulesServiceType {
      SSH
    }

    String ruleName
    AzureLoadBalancerInboundNATRulesServiceType serviceType
    AzureLoadBalancerInboundNATRulesProtocolType protocol
    Integer port
  }

}

/*
  "exception" : {
    "exceptionType" : "RetrofitError",
    "operation" : "stageEnd",
    "details" : {
      "error" : "timeout",
      "errors" : [ ],
      "responseBody" : null,
      "kind" : "NETWORK",
      "status" : null,
      "url" : null
    },
    "shouldRetry" : true
  }
},
  Stage Context: {
    "cloudProvider" : "azure",
    "providerType" : "azure",
    "appName" : "azure1",
    "loadBalancerName" : "azure1-st1-d1",
    "stack" : "st1",
    "detail" : "d1",
    "credentials" : "azure-test",
    "region" : "West US",
    "vnet" : null,
    "probes" : [ {
      "probeName" : "healthcheck1",
      "probeProtocol" : "HTTP",
      "probePort" : 7001,
      "probePath" : "/healthcheck",
      "probeInterval" : 10,
      "unhealthyThreshold" : 2
    } ],
    "securityGroups" : null,
    "loadBalancingRules" : [ {
      "ruleName" : "lbRule1",
      "protocol" : "TCP",
      "externalPort" : "80",
      "backendPort" : "80",
      "probeName" : "healthcheck1",
      "persistence" : "None",
      "idleTimeout" : "4"
    } ],
    "inboundNATRules" : [ {
      "ruleName" : "inboundRule1",
      "serviceType" : "SSH",
      "protocol" : "TCP",
      "port" : "80"
    } ],
    "name" : "azure1-st1-d1",
    "user" : "[anonymous]",
    "stageDetails" : {
      "name" : null,
      "type" : "upsertAmazonLoadBalancer_azure",
      "startTime" : 1447111560073,
      "isSynthetic" : false
    },
    "batch.task.id.stageStart" : 41
  }

*/
