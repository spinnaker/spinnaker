/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import groovy.transform.Immutable

@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class AmazonSecurityGroup implements SecurityGroup {
  final String type
  final String id
  final String name
  final String vpcId
  final String description
  final String application
  final String accountName
  final String accountId
  final String region
  final Set<Rule> inboundRules
  final Set<Rule> outboundRules

  @Override
  SecurityGroupSummary getSummary() {
    new AmazonSecurityGroupSummary(name: name, id: id, vpcId: vpcId)
  }
}
