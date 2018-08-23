/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import lombok.Data;

import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class EcsSecurityGroup implements SecurityGroup {
  final String type = EcsCloudProvider.ID;
  final String cloudProvider = EcsCloudProvider.ID;
  final String id;
  final String name;
  final String vpcId;
  final String description;
  final String application;
  final String accountName;
  final String accountId;
  final String region;
  final Set<Rule> inboundRules;
  final Set<Rule> outboundRules;

  public EcsSecurityGroup(String id,
                          String name,
                          String vpcId,
                          String description,
                          String application,
                          String accountName,
                          String accountId,
                          String region,
                          Set<Rule> inboundRules,
                          Set<Rule> outboundRules) {
    this.id = id;
    this.name = name;
    this.vpcId = vpcId;
    this.description = description;
    this.application = application;
    this.accountName = accountName;
    this.accountId = accountId;
    this.region = region;
    this.inboundRules = inboundRules;
    this.outboundRules = outboundRules;
  }

  @Override
  public SecurityGroupSummary getSummary() {
    return new EcsSecurityGroupSummary(name, id, vpcId);
  }
}
