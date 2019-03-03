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

      // Apply NSG to subnet by using nested ARM template only when a subnet is pointed by users
      if(description.subnet) {
        SecurityGroupSubnet sg_subnet = new SecurityGroupSubnet()
        resources.add(sg_subnet)
      }
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
    NetworkSecurityGroupName networkSecurityGroupName = new NetworkSecurityGroupName()
    NetworkSecurityGroupResourceGroupName networkSecurityGroupResourceGroupName = new NetworkSecurityGroupResourceGroupName()
    VirtualNetworkName virtualNetworkName = new VirtualNetworkName()
    VirtualNetworkResourceGroupName virtualNetworkResourceGroupName = new VirtualNetworkResourceGroupName()
    SubnetName subnetName = new SubnetName()
  }

  static class Location{
    String type = "string"
    Map<String, String> metadata = ["description":"Location to deploy"]
  }

  static class NetworkSecurityGroupName{
    String type = "string"
    Map<String, String> metadata = ["description":"The NSG name"]
  }

  static class NetworkSecurityGroupResourceGroupName{
    String type = "string"
    Map<String, String> metadata = ["description":"The resource group name of NSG"]
  }

  static class VirtualNetworkResourceGroupName{
    String type = "string"
    String defaultValue = ""
    Map<String, String> metadata = ["description":"The resource group name of Virtual Network"]
  }

  static class VirtualNetworkName{
    String type = "string"
    String defaultValue = ""
    Map<String, String> metadata = ["description":"The Virtual Network name"]
  }

  static class SubnetName{
    String type = "string"
    String defaultValue = ""
    Map<String, String> metadata = ["description":"The subnet name"]
  }

  static class SecurityGroup extends DependingResource{
    Map<String, String> tags
    SecurityGroupProperties properties

    SecurityGroup(UpsertAzureSecurityGroupDescription description) {
      apiVersion = "2018-11-01"
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

  /*
  Use ARM nested template to apply NSG to an existing subnet
   */
  static class SecurityGroupSubnet extends DependingResource{
    String resourceGroup
    SecurityGroupSubnetProperties properties

    SecurityGroupSubnet() {
      apiVersion = "2017-08-01"
      name = "nestedTemplate_NSGSubnet"
      type = "Microsoft.Resources/deployments"
      resourceGroup = "[parameters('virtualNetworkResourceGroupName')]"
      dependsOn.add("[parameters('networkSecurityGroupName')]")
      properties = new SecurityGroupSubnetProperties()
    }
  }

  static class SecurityGroupSubnetProperties {
    String mode
    SecurityGroupSubnetPropertiesNestedTemplate template

    SecurityGroupSubnetProperties() {
      mode = "Incremental"
      template = new SecurityGroupSubnetPropertiesNestedTemplate()
    }
  }

  static class SecurityGroupSubnetPropertiesNestedTemplate {
    String $schema
    String contentVersion
    ArrayList<Resource> resources = []

    SecurityGroupSubnetPropertiesNestedTemplate(){
      $schema = "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#"
      contentVersion = "1.0.0.0"
      resources.add(new SecurityGroupSubnetPropertiesNestedTemplateSubnet())
    }
  }

  static class SecurityGroupSubnetPropertiesNestedTemplateSubnet extends Resource {
    SecurityGroupSubnetPropertiesNestedTemplateSubnetProperties properties

    SecurityGroupSubnetPropertiesNestedTemplateSubnet(){
      apiVersion = "2018-11-01"
      type = "Microsoft.Network/virtualNetworks/subnets"
      name = "[concat(parameters('virtualNetworkName'), '/', parameters('subnetName'))]"
      location = "[parameters('location')]"
      properties = new SecurityGroupSubnetPropertiesNestedTemplateSubnetProperties()
    }
  }

  static class SecurityGroupSubnetPropertiesNestedTemplateSubnetProperties {
    String addressPrefix = "[reference(resourceId(parameters('virtualNetworkResourceGroupName'), 'Microsoft.Network/virtualNetworks/subnets', parameters('virtualNetworkName'), parameters('subnetName')), '2018-11-01').addressPrefix]"
    SecurityGroupSubnetPropertiesNestedTemplateSubnetPropertiesNSG networkSecurityGroup = new SecurityGroupSubnetPropertiesNestedTemplateSubnetPropertiesNSG()
  }

  static class SecurityGroupSubnetPropertiesNestedTemplateSubnetPropertiesNSG {
    String id = "[resourceId(parameters('networkSecurityGroupResourceGroupName'), 'Microsoft.Network/networkSecurityGroups', parameters('networkSecurityGroupName'))]"
  }
}

