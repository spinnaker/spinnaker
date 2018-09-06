/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.ResourceCacheData;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryApplication;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.Views;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.*;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toSet;

@Getter
@Slf4j
public class CloudFoundryCachingAgent implements CachingAgent, AccountAware {
  private final String providerName = CloudFoundryProvider.class.getName();
  private final Collection<AgentDataType> providedDataTypes = Arrays.asList(
    AUTHORITATIVE.forType(APPLICATIONS.getNs()),
    AUTHORITATIVE.forType(CLUSTERS.getNs()),
    AUTHORITATIVE.forType(SERVER_GROUPS.getNs()),
    AUTHORITATIVE.forType(INSTANCES.getNs()),
    AUTHORITATIVE.forType(LOAD_BALANCERS.getNs())
  );

  private final ObjectMapper cacheViewMapper = new ObjectMapper()
    .disable(MapperFeature.DEFAULT_VIEW_INCLUSION);

  private final String account;
  private final CloudFoundryClient client;

  public CloudFoundryCachingAgent(String account, CloudFoundryClient client) {
    this.account = account;
    this.client = client;
    this.cacheViewMapper.setConfig(cacheViewMapper.getSerializationConfig().withView(Views.Cache.class));
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    String accountName = getAccountName();
    log.info("Caching all resources in Cloud Foundry account " + accountName);

    Map<String, Collection<CacheData>> results = new HashMap<>();

    List<CloudFoundryApplication> apps = client.getApplications().all();
    List<CloudFoundryLoadBalancer> loadBalancers = client.getRoutes().all();

    results.put(LOAD_BALANCERS.getNs(), loadBalancers.stream()
      .map(lb -> {
        Map<String, Collection<String>> relationships = new HashMap<>();

        relationships.put(SERVER_GROUPS.getNs(), lb.getServerGroups().stream()
          .map(sg -> Keys.getServerGroupKey(accountName, sg.getName(), sg.getRegion()))
          .collect(toSet()));

        return new ResourceCacheData(Keys.getLoadBalancerKey(accountName, lb), cacheView(lb), relationships);
      })
      .collect(toSet()));

    results.put(APPLICATIONS.getNs(), apps.stream()
      .map(app -> {
        Map<String, Collection<String>> relationships = new HashMap<>();

        relationships.put(CLUSTERS.getNs(), app.getClusters().stream()
          .map(cluster -> Keys.getClusterKey(accountName, app.getName(), cluster.getName()))
          .collect(toSet()));

        return new ResourceCacheData(Keys.getApplicationKey(app.getName()), cacheView(app), relationships);
      })
      .collect(toSet()));

    results.put(CLUSTERS.getNs(), apps.stream()
      .flatMap(app ->
        app.getClusters().stream().map(cluster -> {
          Map<String, Collection<String>> relationships = new HashMap<>();

          relationships.put(SERVER_GROUPS.getNs(), cluster.getServerGroups().stream()
            .map(sg -> Keys.getServerGroupKey(accountName, sg.getName(), sg.getRegion()))
            .collect(toSet()));

          return new ResourceCacheData(Keys.getClusterKey(accountName, app.getName(), cluster.getName()),
            cacheView(cluster), relationships);
        })
      )
      .collect(toSet()));

    results.put(SERVER_GROUPS.getNs(), apps.stream()
      .flatMap(app -> app.getClusters().stream()
        .flatMap(cluster -> cluster.getServerGroups().stream()
          .map(serverGroup -> {
            Map<String, Collection<String>> relationships = new HashMap<>();

            relationships.put(INSTANCES.getNs(), serverGroup.getInstances().stream()
              .map(inst -> Keys.getInstanceKey(accountName, inst.getName()))
              .collect(toSet()));
            relationships.put(LOAD_BALANCERS.getNs(), loadBalancers.stream()
              .filter(lb -> lb.getServerGroups().stream()
                .anyMatch(lbSg -> lbSg.getName().equals(serverGroup.getName()) &&
                  lbSg.getRegion().equals(serverGroup.getRegion()) &&
                  lbSg.getAccount().equals(accountName))
              )
              .map(lb -> Keys.getLoadBalancerKey(accountName, lb))
              .collect(toSet()));

            return new ResourceCacheData(Keys.getServerGroupKey(accountName, serverGroup.getName(), serverGroup.getRegion()),
              cacheView(serverGroup), relationships);
          })
        )
      )
      .collect(toSet()));

    results.put(INSTANCES.getNs(), apps.stream()
      .flatMap(app -> app.getClusters().stream())
      .flatMap(cluster -> cluster.getServerGroups().stream())
      .flatMap(serverGroup -> serverGroup.getInstances().stream())
      .map(inst -> new ResourceCacheData(Keys.getInstanceKey(accountName, inst.getName()), cacheView(inst), emptyMap()))
      .collect(toSet()));

    return new DefaultCacheResult(results);
  }

  @Override
  public String getAccountName() {
    return account;
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getClass().getSimpleName();
  }

  /**
   * Serialize just enough data to be able to reconstitute the model fully if its relationships are also deserialized.
   */
  private Map<String, Object> cacheView(Object o) {
    return cacheViewMapper.convertValue(o, new TypeReference<Map<String, Object>>() {
    });
  }
}
