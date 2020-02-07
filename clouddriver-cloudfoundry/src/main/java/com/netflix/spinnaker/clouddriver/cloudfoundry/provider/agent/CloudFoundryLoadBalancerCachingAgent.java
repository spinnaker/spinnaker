/*
 * Copyright 2019 Pivotal, Inc.
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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.ResourceCacheData;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import io.vavr.collection.HashMap;
import java.util.*;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class CloudFoundryLoadBalancerCachingAgent extends AbstractCloudFoundryCachingAgent {
  private static final ObjectMapper cacheViewMapper =
      new ObjectMapper().disable(MapperFeature.DEFAULT_VIEW_INCLUSION);

  private final Collection<AgentDataType> providedDataTypes =
      Collections.singletonList(AUTHORITATIVE.forType(LOAD_BALANCERS.getNs()));

  public CloudFoundryLoadBalancerCachingAgent(
      String account, CloudFoundryClient client, Registry registry) {
    super(account, client, registry);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    long loadDataStart = this.getInternalClock().millis();
    String accountName = getAccountName();
    log.info("Caching all load balancers (routes) in Cloud Foundry account " + accountName);
    List<CloudFoundryLoadBalancer> loadBalancers = this.getClient().getRoutes().all();

    Collection<CacheData> onDemandCacheData =
        providerCache.getAll(
            ON_DEMAND.getNs(),
            providerCache.filterIdentifiers(ON_DEMAND.getNs(), Keys.getAllLoadBalancers()));

    List<String> toEvict = new ArrayList<>();
    Map<String, CacheData> toKeep = new java.util.HashMap<>();
    onDemandCacheData.forEach(
        cacheData -> {
          long cacheTime = (long) cacheData.getAttributes().get("cacheTime");
          if (cacheTime < loadDataStart
              && (int) cacheData.getAttributes().get("processedCount") > 0) {
            toEvict.add(cacheData.getId());
          } else {
            toKeep.put(cacheData.getId(), cacheData);
          }
        });

    Map<String, Collection<CacheData>> results =
        HashMap.<String, Collection<CacheData>>of(
                LOAD_BALANCERS.getNs(),
                loadBalancers.stream()
                    .map(lb -> setCacheData(toKeep, lb, loadDataStart))
                    .collect(toSet()))
            .toJavaMap();

    onDemandCacheData.forEach(this::processOnDemandCacheData);
    results.put(ON_DEMAND.getNs(), toKeep.values());

    log.debug(
        "LoadBalancer cache loaded for Cloud Foundry account {}, ({} sec)",
        accountName,
        (getInternalClock().millis() - loadDataStart) / 1000);
    return new DefaultCacheResult(results, Collections.singletonMap(ON_DEMAND.getNs(), toEvict));
  }

  private CacheData setCacheData(
      Map<String, CacheData> onDemandCacheDataToKeep,
      CloudFoundryLoadBalancer cloudFoundryLoadBalancer,
      long start) {
    String account = this.getAccount();
    String key = Keys.getLoadBalancerKey(account, cloudFoundryLoadBalancer);
    CacheData lbCacheData = onDemandCacheDataToKeep.get(key);
    if (lbCacheData != null && (long) lbCacheData.getAttributes().get("cacheTime") > start) {
      Map<String, Collection<ResourceCacheData>> cacheResults =
          getCacheResultsFromCacheData(lbCacheData);
      onDemandCacheDataToKeep.remove(key);
      return cacheResults.get(LOAD_BALANCERS.getNs()).stream().findFirst().orElse(null);
    } else {
      return new ResourceCacheData(
          Keys.getLoadBalancerKey(account, cloudFoundryLoadBalancer),
          cacheView(cloudFoundryLoadBalancer),
          singletonMap(
              SERVER_GROUPS.getNs(),
              cloudFoundryLoadBalancer.getServerGroups().stream()
                  .map(sg -> Keys.getServerGroupKey(account, sg.getName(), sg.getRegion()))
                  .collect(toSet())));
    }
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandAgent.OnDemandType.LoadBalancer)
        && cloudProvider.equals(CloudFoundryProvider.PROVIDER_ID);
  }

  @Nullable
  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    String account = Optional.ofNullable(data.get("account")).map(Object::toString).orElse(null);
    String region = Optional.ofNullable(data.get("region")).map(Object::toString).orElse(null);
    String loadBalancerName =
        Optional.ofNullable(data.get("loadBalancerName")).map(Object::toString).orElse(null);
    if (account == null || region == null || loadBalancerName == null) {
      return null;
    }
    CloudFoundrySpace space = getClient().getOrganizations().findSpaceByRegion(region).orElse(null);
    if (space == null) {
      return null;
    }
    log.info("On Demand cache refresh triggered, waiting for load balancers loadData to be called");
    RouteId routeId = this.getClient().getRoutes().toRouteId(loadBalancerName);
    if (routeId == null) {
      return null;
    }
    CloudFoundryLoadBalancer cloudFoundryLoadBalancer =
        this.getClient().getRoutes().find(routeId, space.getId());
    String loadBalancerKey =
        Optional.ofNullable(cloudFoundryLoadBalancer)
            .map(lb -> Keys.getLoadBalancerKey(account, lb))
            .orElse(Keys.getLoadBalancerKey(this.getAccount(), loadBalancerName, region));
    Map<String, Collection<String>> evictions;

    DefaultCacheResult loadBalancerCacheResults;

    if (cloudFoundryLoadBalancer != null) {
      Collection<CacheData> loadBalancerCacheData =
          Collections.singleton(
              new ResourceCacheData(
                  loadBalancerKey,
                  cacheView(cloudFoundryLoadBalancer),
                  singletonMap(
                      SERVER_GROUPS.getNs(),
                      cloudFoundryLoadBalancer.getServerGroups().stream()
                          .map(sg -> Keys.getServerGroupKey(account, sg.getName(), sg.getRegion()))
                          .collect(toSet()))));

      loadBalancerCacheResults =
          new DefaultCacheResult(
              Collections.singletonMap(LOAD_BALANCERS.getNs(), loadBalancerCacheData));

      providerCache.putCacheData(
          ON_DEMAND.getNs(),
          buildOnDemandCacheData(loadBalancerKey, loadBalancerCacheResults.getCacheResults()));
      evictions = Collections.emptyMap();
    } else {
      loadBalancerCacheResults =
          new DefaultCacheResult(
              Collections.singletonMap(LOAD_BALANCERS.getNs(), Collections.emptyList()));
      evictions =
          Collections.singletonMap(
              LOAD_BALANCERS.getNs(),
              providerCache.filterIdentifiers(LOAD_BALANCERS.getNs(), loadBalancerKey));
    }

    return new OnDemandResult(getOnDemandAgentType(), loadBalancerCacheResults, evictions);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> keys =
        providerCache.filterIdentifiers(ON_DEMAND.getNs(), Keys.getAllLoadBalancers());
    return providerCache.getAll(ON_DEMAND.getNs(), keys, RelationshipCacheFilter.none()).stream()
        .map(
            it -> {
              String loadbalancerId = it.getId();
              Map<String, String> details = Keys.parse(loadbalancerId).orElse(emptyMap());
              Map<String, Object> attributes = it.getAttributes();

              return HashMap.of(
                      "id",
                      loadbalancerId,
                      "details",
                      details,
                      "moniker",
                      convertOnDemandDetails(details),
                      "cacheTime",
                      attributes.get("cacheTime"),
                      "cacheExpiry",
                      attributes.get("cacheExpiry"),
                      "processedCount",
                      attributes.get("processedCount"),
                      "processedTime",
                      attributes.get("processedTime"))
                  .toJavaMap();
            })
        .collect(toSet());
  }
}
