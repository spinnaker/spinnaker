/*
 * Copyright 2019 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import java.util.Set;

public class AliCloudSecurityGroup implements SecurityGroup {

  String type = AliCloudProvider.ID;
  String cloudProvider = AliCloudProvider.ID;
  String id;
  String name;
  String application;
  String accountName;
  String region;
  Set<Rule> inboundRules;
  Set<Rule> outboundRules;
  String vpcId;

  public AliCloudSecurityGroup(
      String id,
      String name,
      String application,
      String accountName,
      String region,
      String vpcId,
      Set<Rule> inboundRules) {
    this.id = id;
    this.name = name;
    this.application = application;
    this.accountName = accountName;
    this.region = region;
    this.vpcId = vpcId;
    this.inboundRules = inboundRules;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getApplication() {
    return application;
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public Set<Rule> getInboundRules() {
    return inboundRules;
  }

  @Override
  public Set<Rule> getOutboundRules() {
    return outboundRules;
  }

  @Override
  public SecurityGroupSummary getSummary() {
    return new AliCloudSecurityGroupSummary(name, id, vpcId);
  }
}
