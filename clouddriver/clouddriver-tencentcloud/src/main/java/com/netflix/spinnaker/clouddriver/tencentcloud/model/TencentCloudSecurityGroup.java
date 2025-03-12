/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.tencentcloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.netflix.spinnaker.clouddriver.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;

@JsonInclude(Include.NON_EMPTY)
@Data
public class TencentCloudSecurityGroup implements SecurityGroup {

  private final String type = TencentCloudProvider.ID;
  private final String cloudProvider = TencentCloudProvider.ID;
  // securityGroupId
  private String id;
  // securityGroupName
  private String name;
  private String description;
  private String application;
  private String accountName;
  private String region;
  private Set<Rule> inboundRules = new HashSet<>();
  private Set<Rule> outboundRules = new HashSet<>();
  private List<TencentCloudSecurityGroupRule> inRules = new ArrayList<>();
  private List<TencentCloudSecurityGroupRule> outRules = new ArrayList<>();

  public TencentCloudSecurityGroup(
      String id,
      String name,
      String description,
      String application,
      String accountName,
      String region,
      Set<Rule> inboundRules,
      Set<Rule> outboundRules,
      List<TencentCloudSecurityGroupRule> inRules,
      List<TencentCloudSecurityGroupRule> outRules) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.application = application;
    this.accountName = accountName;
    this.region = region;
    this.inboundRules.addAll(inboundRules);
    this.outboundRules.addAll(outboundRules);
    this.inRules.addAll(inRules);
    this.outRules.addAll(outRules);
  }

  @Override
  public SecurityGroupSummary getSummary() {
    return new TencentCloudSecurityGroupSummary(name, id);
  }
}
