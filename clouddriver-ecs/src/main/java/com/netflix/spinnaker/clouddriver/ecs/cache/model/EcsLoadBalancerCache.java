/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import java.util.List;
import java.util.Set;
import lombok.Data;

@Data
public class EcsLoadBalancerCache implements LoadBalancer {

  private String account;
  private String region;
  private String loadBalancerArn;
  private String loadBalancerType;
  private String cloudProvider = EcsCloudProvider.ID;
  private List<Listener> listeners;
  private String scheme;
  private List<String> availabilityZones;
  private String ipAddressType;
  private String loadBalancerName;
  private String canonicalHostedZoneId;
  private String vpcId;
  private String dnsname;
  private Long createdTime;
  private List<String> subnets;
  private List<String> securityGroups;
  private List<String> targetGroups;
  // private List<Object> state;
  private Set<LoadBalancerServerGroup> serverGroups;

  @Override
  public String getName() {
    return loadBalancerName;
  }

  @Override
  public String getType() {
    return loadBalancerType;
  }
}
