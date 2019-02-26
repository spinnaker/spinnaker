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

package com.netflix.spinnaker.clouddriver.azure.templates

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroupDescription.AzureSGRule
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.UpsertAzureSecurityGroupDescription

class AzureSecurityGroupResourceTemplate {
  static ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)

  static String getTemplate(UpsertAzureSecurityGroupDescription description) {
    SecurityGroupTemplate template = new SecurityGroupTemplate(description)

    mapper.writeValueAsString(template)
  }

  static class SecurityGroupTemplate{
    //TODO: Make this configurable for AZURE_US_GOVERNMENT
    String $schema = "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#"
    String contentVersion = "1.0.0.0"

    SecurityGroupParameters parameters
    SecurityGroupTemplateVariables variables
    ArrayList<Resource> resources = []

    SecurityGroupTemplate(UpsertAzureSecurityGroupDescription description) {
      parameters = new SecurityGroupParameters()
      variables = new SecurityGroupTemplateVariables(description)

      SecurityGroup sg = new SecurityGroup(description)

      resources.add(sg)
    }
  }

  static class SecurityGroupTemplateVariables{
    String securityGroupName

    SecurityGroupTemplateVariables(UpsertAzureSecurityGroupDescription description){
      securityGroupName = description.securityGroupName.toLowerCase()
    }
  }

  static class SecurityGroupParameters{
    Location location = new Location()
  }

  static class Location{
    String type = "string"
    Map<String, String> metadata = ["description":"Location to deploy"]
  }

  static class SecurityGroup extends DependingResource{
    Map<String, String> tags
    SecurityGroupProperties properties

    SecurityGroup(UpsertAzureSecurityGroupDescription description) {
      apiVersion = "2015-05-01-preview"
      name = "[variables('securityGroupName')]"
      type = "Microsoft.Network/networkSecurityGroups"
      location = "[parameters('location')]"
      def currentTime = System.currentTimeMillis()
      tags = [:]
      tags.appName = description.appName
      tags.stack = description.stack ?: "none"
      tags.detail = description.detail ?: "none"
      tags.createdTime = currentTime.toString()

      properties = new SecurityGroupProperties(description)
    }
  }

  static class SecurityGroupProperties{
    ArrayList<AzureNSGRule> securityRules = []

    SecurityGroupProperties(UpsertAzureSecurityGroupDescription description){
      description.securityRules?.each { rule -> securityRules.add(new AzureNSGRule(rule))}
    }
  }

  static class AzureNSGRule {
    String name
    AzureNSGRuleProperties properties

    AzureNSGRule(AzureSGRule rule) {
      name = rule.name
      properties = new AzureNSGRuleProperties(rule)
    }
  }

  static class AzureNSGRuleProperties {
    String description /* restricted to 140 chars */
    String access /* gets or sets network traffic is allowed or denied; possible values are “Allow” and “Deny” */
    String destinationAddressPrefix /* CIDR or destination IP range; asterix “*” can also be used to match all source IPs; default tags such as ‘VirtualNetwork’, ‘AzureLoadBalancer’ and ‘Internet’ can also be used */
    String destinationPortRange /* Integer or range between 0 and 65535; asterix “*” can also be used to match all ports */
    String direction /* InBound or Outbound */
    Integer priority /* value can be between 100 and 4096 */
    String protocol /* Tcp, Udp or All(*) */
    String sourceAddressPrefix /* CIDR or source IP range; asterix “*” can also be used to match all source IPs; default tags such as ‘VirtualNetwork’, ‘AzureLoadBalancer’ and ‘Internet’ can also be used */
    String sourcePortRange /* Integer or range between 0 and 65535; asterix “*” can also be used to match all ports */

    AzureNSGRuleProperties(AzureSGRule rule) {
      description = rule.description
      access = rule.access
      destinationAddressPrefix = rule.destinationAddressPrefix
      destinationPortRange = rule.destinationPortRange
      direction = rule.direction
      priority = rule.priority
      protocol = rule.protocol
      sourceAddressPrefix = rule.sourceAddressPrefix
      sourcePortRange = rule.sourcePortRange
    }
  }
}

