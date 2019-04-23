/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.Immutable

@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class GoogleSecurityGroup implements SecurityGroup {
  final String type = GoogleCloudProvider.ID
  final String cloudProvider = GoogleCloudProvider.ID
  final String id
  final String name
  final String description
  final String application
  final String accountName
  final String region
  final String network
  final String selfLink

  // GCE firewall rules (modeled by this class) can either use sourceTags/targetTags or
  // sourceServiceAccounts/targetServiceAccounts.
  // Read more at https://cloud.google.com/vpc/docs/firewalls#service-accounts-vs-tags.

  // Don't see an elegant way to encapsulate source tags in an inbound rule.
  final List<String> sourceTags
  final List<String> targetTags

  final List<String> sourceServiceAccounts
  final List<String> targetServiceAccounts

  final Set<Rule> inboundRules
  final Set<Rule> outboundRules

  @Override
  SecurityGroupSummary getSummary() {
    new GoogleSecurityGroupSummary(name: name, id: id, network: network, selfLink: selfLink, sourceTags: sourceTags, targetTags: targetTags)
  }
}
