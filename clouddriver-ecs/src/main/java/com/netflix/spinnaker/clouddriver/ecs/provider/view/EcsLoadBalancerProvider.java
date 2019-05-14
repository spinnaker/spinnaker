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

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancer;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerDetail;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerSummary;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsLoadBalancerProvider implements LoadBalancerProvider<AmazonLoadBalancer> {

  private final EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient;
  private final ECSCredentialsConfig ecsCredentialsConfig;

  @Autowired
  public EcsLoadBalancerProvider(
      EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient,
      ECSCredentialsConfig ecsCredentialsConfig) {
    this.ecsLoadbalancerCacheClient = ecsLoadbalancerCacheClient;
    this.ecsCredentialsConfig = ecsCredentialsConfig;
  }

  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public List<Item> list() {
    Map<String, EcsLoadBalancerSummary> map = new HashMap<>();
    List<EcsLoadBalancerCache> loadBalancers = ecsLoadbalancerCacheClient.findAll();

    for (EcsLoadBalancerCache lb : loadBalancers) {
      String account = getEcsAccountName(lb.getAccount());
      if (account == null) {
        continue;
      }

      String name = lb.getLoadBalancerName();
      String region = lb.getRegion();

      EcsLoadBalancerSummary summary = map.get(name);
      if (summary == null) {
        summary = new EcsLoadBalancerSummary().withName(name);
        map.put(name, summary);
      }

      EcsLoadBalancerDetail loadBalancer = new EcsLoadBalancerDetail();
      loadBalancer.setAccount(account);
      loadBalancer.setRegion(region);
      loadBalancer.setName(name);
      loadBalancer.setVpcId(lb.getVpcId());
      loadBalancer.setSecurityGroups(lb.getSecurityGroups());
      loadBalancer.setLoadBalancerType(lb.getLoadBalancerType());
      loadBalancer.setTargetGroups(lb.getTargetGroups());

      summary
          .getOrCreateAccount(account)
          .getOrCreateRegion(region)
          .getLoadBalancers()
          .add(loadBalancer);
    }

    return new ArrayList<>(map.values());
  }

  @Override
  public Item get(String name) {
    return null; // TODO - Implement this.
  }

  @Override
  public List<Details> byAccountAndRegionAndName(String account, String region, String name) {
    return null; // TODO - Implement this.  This is used to show the details view of a load balancer
    // which is not even implemented yet
  }

  @Override
  public Set<AmazonLoadBalancer> getApplicationLoadBalancers(String application) {
    return null; // TODO - Implement this.  This is used to show load balancers and reveals other
    // buttons
  }

  private String getEcsAccountName(String awsAccountName) {
    for (ECSCredentialsConfig.Account ecsAccount : ecsCredentialsConfig.getAccounts()) {
      if (ecsAccount.getAwsAccount().equals(awsAccountName)) {
        return ecsAccount.getName();
      }
    }
    return null;
  }
}
