/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.provider.view;

import static com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunInstance;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunServerGroup;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudrunLoadBalancerProvider implements LoadBalancerProvider<CloudrunLoadBalancer> {

  private final String cloudProvider = CloudrunCloudProvider.ID;
  @Autowired private Cache cacheView;
  @Autowired private ObjectMapper objectMapper;

  @Override
  public List<Item> list() {
    return null;
  }

  @Override
  public Item get(String name) {
    return null;
  }

  @Override
  public List<Details> byAccountAndRegionAndName(String account, String region, String name) {
    return null;
  }

  @Override
  public Set<CloudrunLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    String applicationKey = Keys.getApplicationKey(applicationName);
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.getNs(), applicationKey);

    Collection<CacheData> applicationLoadBalancers =
        CloudrunProviderUtils.resolveRelationshipData(
            cacheView, application, Keys.Namespace.LOAD_BALANCERS.getNs());
    return translateLoadBalancers(applicationLoadBalancers);
  }

  public Set<CloudrunLoadBalancer> translateLoadBalancers(Collection<CacheData> cacheData) {
    Set<CloudrunLoadBalancer> loadBalancers = new HashSet<>();
    cacheData.forEach(
        loadBalancerData -> {
          Set<CloudrunServerGroup> serverGroups = new HashSet<>();
          CloudrunProviderUtils.resolveRelationshipData(
                  cacheView, loadBalancerData, SERVER_GROUPS.getNs())
              .forEach(
                  serverGroupRelationshipData -> {
                    Set<CloudrunInstance> instances =
                        CloudrunProviderUtils.resolveRelationshipData(
                                cacheView, serverGroupRelationshipData, INSTANCES.getNs())
                            .stream()
                            .map(
                                instanceRelationshipDate ->
                                    CloudrunProviderUtils.instanceFromCacheData(
                                        objectMapper, instanceRelationshipDate))
                            .collect(Collectors.toSet());
                    serverGroups.add(
                        CloudrunProviderUtils.serverGroupFromCacheData(
                            objectMapper, serverGroupRelationshipData, instances));
                  });
          CloudrunLoadBalancer loadBalancer =
              CloudrunProviderUtils.loadBalancerFromCacheData(
                  objectMapper, loadBalancerData, serverGroups);
          loadBalancers.add(loadBalancer);
        });
    return loadBalancers;
  }

  public CloudrunLoadBalancer getLoadBalancer(String account, String loadBalancerName) {
    String loadBalancerKey = Keys.getLoadBalancerKey(account, loadBalancerName);
    CacheData loadBalancerData = cacheView.get(LOAD_BALANCERS.getNs(), loadBalancerKey);
    if (loadBalancerData == null) {
      return null;
    }
    Set<CloudrunLoadBalancer> loadBalancers = translateLoadBalancers(Set.of(loadBalancerData));
    return loadBalancers.isEmpty() ? null : loadBalancers.stream().findFirst().get();
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }
}
