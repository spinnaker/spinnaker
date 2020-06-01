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

package com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer;

import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudBasicResource;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.List;
import java.util.Set;
import lombok.Data;

@Data
public class TencentCloudLoadBalancer implements LoadBalancer, TencentCloudBasicResource {

  private final String cloudProvider = TencentCloudProvider.ID;
  private final String type = TencentCloudProvider.ID;
  private String application;
  private String accountName;
  private String region;
  private String id;
  private String name;
  private String loadBalancerId;
  private String loadBalancerName;
  private String loadBalancerType;
  private Integer forwardType;
  private String vpcId;
  private String subnetId;
  private Integer projectId;
  private String createTime;
  private List<String> loadBalancerVips;
  private List<String> securityGroups;
  private List<TencentCloudLoadBalancerListener> listeners;
  private Set<LoadBalancerServerGroup> serverGroups;

  @Override
  public String getAccount() {
    return accountName;
  }

  @Override
  public Moniker getMoniker() {
    return NamerRegistry.lookup()
        .withProvider(TencentCloudProvider.ID)
        .withAccount(accountName)
        .withResource(TencentCloudBasicResource.class)
        .deriveMoniker(this);
  }

  @Override
  public String getMonikerName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TencentCloudLoadBalancer)) {
      return false;
    }

    TencentCloudLoadBalancer other = (TencentCloudLoadBalancer) o;
    return other.getAccount().equals(this.getAccount())
        && other.getName().equals(this.getName())
        && other.getType().equals(this.getType())
        && other.getId().equals(this.getId())
        && other.getRegion().equals(this.getRegion());
  }

  @Override
  public int hashCode() {
    return getId().hashCode() + getType().hashCode();
  }
}
