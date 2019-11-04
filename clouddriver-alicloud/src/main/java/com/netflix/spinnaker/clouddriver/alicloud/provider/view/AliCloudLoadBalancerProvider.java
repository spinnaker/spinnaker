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
package com.netflix.spinnaker.clouddriver.alicloud.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudLoadBalancerProvider implements LoadBalancerProvider<AliCloudLoadBalancer> {

  private final ObjectMapper objectMapper;

  private final Cache cacheView;

  @Autowired
  public AliCloudLoadBalancerProvider(ObjectMapper objectMapper, Cache cacheView) {
    this.objectMapper = objectMapper;
    this.cacheView = cacheView;
  }

  @Override
  public Set<AliCloudLoadBalancer> getApplicationLoadBalancers(String application) {
    Set<AliCloudLoadBalancer> set = new HashSet<AliCloudLoadBalancer>();

    Collection<String> allLoadBalancerKeys = cacheView.getIdentifiers(LOAD_BALANCERS.ns);
    List<String> loadBalancerKeys =
        allLoadBalancerKeys.stream()
            .filter(stu -> applicationMatcher(stu, application))
            .collect(Collectors.toList());
    Collection<CacheData> loadBalancers =
        cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys, null);

    for (CacheData loadBalancer : loadBalancers) {

      Map<String, Object> map = objectMapper.convertValue(loadBalancer.getAttributes(), Map.class);
      set.add(
          new AliCloudLoadBalancer(
              String.valueOf(map.get("account")),
              String.valueOf(map.get("regionIdAlias")),
              String.valueOf(map.get("loadBalancerName")),
              String.valueOf(map.get("vpcId")),
              String.valueOf(map.get("loadBalancerId"))));
    }

    return set;
  }

  @Override
  public List<ResultDetails> byAccountAndRegionAndName(String account, String region, String name) {
    List<ResultDetails> results = new ArrayList<>();
    String searchKey = Keys.getLoadBalancerKey(name, account, region, null) + "*";
    Collection<String> allLoadBalancerKeys =
        cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey);
    Collection<CacheData> loadBalancers =
        cacheView.getAll(LOAD_BALANCERS.ns, allLoadBalancerKeys, null);
    for (CacheData loadBalancer : loadBalancers) {
      ResultDetails resultDetails = new ResultDetails();
      resultDetails.setResults(loadBalancer.getAttributes());
      results.add(resultDetails);
    }

    return results;
  }

  class ResultDetails implements Details {

    Map results;

    public Map getResults() {
      return results;
    }

    public void setResults(Map results) {
      this.results = results;
    }
  }

  @Override
  public String getCloudProvider() {
    return AliCloudProvider.ID;
  }

  @Override
  public List<? extends Item> list() {
    return null;
  }

  @Override
  public Item get(String name) {
    return null;
  }

  private static boolean applicationMatcher(String key, String applicationName) {
    String provider = "alicloud";
    boolean a = key.indexOf(provider) > -1;
    boolean b = key.indexOf(applicationName) > -1;
    return a && b;
  }
}
