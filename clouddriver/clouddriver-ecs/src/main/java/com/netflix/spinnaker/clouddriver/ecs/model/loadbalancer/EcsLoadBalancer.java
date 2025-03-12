/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer;

import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class EcsLoadBalancer implements LoadBalancer {
  // TODO: refactor EcsLoadBalancerCache so can be extended here?

  private String account;
  private String region;
  private String loadBalancerArn;
  private String loadBalancerType;
  private String cloudProvider = EcsCloudProvider.ID;
  private List<Listener> listeners;
  private List<String> availabilityZones;
  private String ipAddressType;
  private String loadBalancerName;
  private String canonicalHostedZoneId;
  private String vpcId;
  private String dnsname;
  private Long createdTime;
  private List<String> subnets;
  private List<String> securityGroups;
  private List<EcsTargetGroup> targetGroups;
  private Set<LoadBalancerServerGroup> serverGroups;
  private Map<String, Set<String>> targetGroupServices;

  @Override
  public String getName() {
    return loadBalancerName;
  }

  @Override
  public String getType() {
    return cloudProvider;
  }
}
