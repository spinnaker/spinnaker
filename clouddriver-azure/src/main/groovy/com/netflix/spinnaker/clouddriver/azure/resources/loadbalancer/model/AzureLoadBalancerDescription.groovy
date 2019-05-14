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

import com.microsoft.azure.management.network.LoadBalancer
import com.microsoft.azure.management.network.TransportProtocol
import com.microsoft.azure.management.network.implementation.LoadBalancerInner
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureLoadBalancerResourceTemplate
import groovy.transform.CompileStatic

@CompileStatic
class AzureLoadBalancerDescription extends AzureResourceOpsDescription {
  String loadBalancerName
  String vnet
  String subnet
  String securityGroup
  String dnsName
  String cluster
  List<String> serverGroups
  String appName
  String sessionPersistence
  List<AzureLoadBalancerProbe> probes = []
  List<AzureLoadBalancingRule> loadBalancingRules = []
  List<AzureLoadBalancerInboundNATRule> inboundNATRules = []

  static class AzureLoadBalancerProbe {
    enum AzureLoadBalancerProbesType {
      TCP, HTTP, HTTPS
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

  static AzureLoadBalancerDescription build(LoadBalancerInner azureLoadBalancer) {
    AzureLoadBalancerDescription description = new AzureLoadBalancerDescription(loadBalancerName: azureLoadBalancer.name())
    def parsedName = Names.parseName(azureLoadBalancer.name())
    description.stack = azureLoadBalancer.tags?.stack ?: parsedName.stack
    description.detail = azureLoadBalancer.tags?.detail ?: parsedName.detail
    description.appName = azureLoadBalancer.tags?.appName ?: parsedName.app
    description.cluster = azureLoadBalancer.tags?.cluster
    description.vnet = azureLoadBalancer.tags?.vnet
    description.createdTime = azureLoadBalancer.tags?.createdTime?.toLong()
    description.tags.putAll(azureLoadBalancer.tags)
    description.region = azureLoadBalancer.location()

    // Each load balancer backend address pool corresponds to a server group (except the "default_LB_BAP")
    description.serverGroups = []
    azureLoadBalancer.backendAddressPools()?.each { bap ->
      if (bap.name() != AzureLoadBalancerResourceTemplate.DEFAULT_BACKEND_POOL) description.serverGroups << bap.name()
    }

    for (def rule : azureLoadBalancer.loadBalancingRules()) {
      def r = new AzureLoadBalancingRule(ruleName: rule.name())
      r.externalPort = rule.frontendPort()
      r.backendPort = rule.backendPort()
      r.probeName = AzureUtilities.getNameFromResourceId(rule?.probe()?.id()) ?: "not-assigned"
      r.persistence = rule.loadDistribution()
      r.idleTimeout = rule.idleTimeoutInMinutes()

      if (rule.protocol() == TransportProtocol.UDP) {
        r.protocol = AzureLoadBalancingRule.AzureLoadBalancingRulesType.UDP
      } else {
        r.protocol = AzureLoadBalancingRule.AzureLoadBalancingRulesType.TCP
      }
      description.loadBalancingRules.add(r)
    }

    // Add the probes
    for (def probe : azureLoadBalancer.probes()) {
      def p = new AzureLoadBalancerProbe()
      p.probeName = probe.name()
      p.probeInterval = probe.intervalInSeconds()
      p.probePath = probe.requestPath()
      p.probePort = probe.port()
      p.unhealthyThreshold = probe.numberOfProbes()
      if (probe.protocol() == TransportProtocol.TCP) {
        p.probeProtocol = AzureLoadBalancerProbe.AzureLoadBalancerProbesType.TCP
      } else {
        p.probeProtocol = AzureLoadBalancerProbe.AzureLoadBalancerProbesType.HTTP
      }
      description.probes.add(p)
    }

    for (def natRule : azureLoadBalancer.inboundNatRules()) {
      def n = new AzureLoadBalancerInboundNATRule(ruleName: natRule.name())
      description.inboundNATRules.add(n)
    }

    description
  }

}
