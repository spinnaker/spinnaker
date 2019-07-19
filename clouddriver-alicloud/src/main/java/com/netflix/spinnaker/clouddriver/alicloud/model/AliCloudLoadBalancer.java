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
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AliCloudLoadBalancer implements LoadBalancer {

  String account;

  String region;

  String name;

  String loadBalancerId;

  String type = AliCloudProvider.ID;

  String cloudProvider = AliCloudProvider.ID;

  String vpcId;

  Set<LoadBalancerServerGroup> serverGroups = new HashSet<>();

  Map<String, String> labels = new HashMap<>();

  Moniker moniker = new Moniker();

  public AliCloudLoadBalancer(
      String account, String region, String name, String vpcId, String loadBalancerId) {
    this.account = account;
    this.region = region;
    this.name = name;
    this.vpcId = vpcId;
    this.loadBalancerId = loadBalancerId;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Moniker getMoniker() {
    return moniker;
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
  public String getAccount() {
    return account;
  }

  @Override
  public Set<LoadBalancerServerGroup> getServerGroups() {
    return serverGroups;
  }

  @Override
  public Map<String, String> getLabels() {
    return labels;
  }

  public String getRegion() {
    return region;
  }

  public String getVpcId() {
    return vpcId;
  }

  public String getLoadBalancerId() {
    return loadBalancerId;
  }
}
