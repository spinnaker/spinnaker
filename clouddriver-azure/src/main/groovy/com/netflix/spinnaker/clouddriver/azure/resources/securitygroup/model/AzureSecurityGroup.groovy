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

package com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable

@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class AzureSecurityGroup implements SecurityGroup {
  final String type = "azure"
  final String id
  final String name
  final String application
  final String accountName
  final String region
  final String network
  final String account
  final List<String> subnets = []
  final Map<String,String> tags = [:]
  final Set<Rule> inboundRules = []
  final Set<Rule> outboundRules = []
  final List<AzureSecurityGroupDescription.AzureSGRule> securityRules = []

  @Override
  SecurityGroupSummary getSummary() {
    new AzureSecurityGroupSummary(name: name, id: id, network: network)
  }
}

@Immutable
@EqualsAndHashCode(includes = ['id', 'network'], cache = true)
class AzureSecurityGroupSummary implements SecurityGroupSummary {
  String name
  String id
  String network
}
