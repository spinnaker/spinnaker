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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
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
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.moniker.Moniker;
import io.vavr.collection.HashMap;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class CloudFoundryServerGroupCachingAgent extends AbstractCloudFoundryCachingAgent {
  private static final ObjectMapper cacheViewMapper =
      new ObjectMapper().disable(MapperFeature.DEFAULT_VIEW_INCLUSION);

  private final Collection<AgentDataType> providedDataTypes =
      Arrays.asList(
          AUTHORITATIVE.forType(APPLICATIONS.getNs()),
          AUTHORITATIVE.forType(CLUSTERS.getNs()),
          AUTHORITATIVE.forType(SERVER_GROUPS.getNs()),
          AUTHORITATIVE.forType(INSTANCES.getNs()));

  public CloudFoundryServerGroupCachingAgent(
      String account, CloudFoundryClient client, Registry registry) {
    super(account, client, registry);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    long loadDataStart = this.getInternalClock().millis();
    String accountName = getAccountName();
    log.info("Caching all resources in Cloud Foundry account " + accountName);

    List<CloudFoundryApplication> apps = this.getClient().getApplications().all();
    List<CloudFoundryCluster> clusters =
        apps.stream().flatMap(app -> app.getClusters().stream()).collect(Collectors.toList());
    List<CloudFoundryServerGroup> serverGroups =
        clusters.stream()
            .flatMap(cluster -> cluster.getServerGroups().stream())
            .collect(Collectors.toList());
    List<CloudFoundryInstance> instances =
        serverGroups.stream()
            .flatMap(serverGroup -> serverGroup.getInstances().stream())
            .collect(Collectors.toList());
    Collection<CacheData> onDemandCacheData =
        providerCache.getAll(
            ON_DEMAND.getNs(),
            providerCache.filterIdentifiers(
                ON_DEMAND.getNs(), Keys.getServerGroupKey(accountName, "*", "*")));

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
        HashMap.<String, Collection<CacheData>>empty().toJavaMap();
    results.put(
        APPLICATIONS.getNs(),
        apps.stream().map(this::buildApplicationCacheData).collect(Collectors.toSet()));
    results.put(
        CLUSTERS.getNs(),
        clusters.stream().map(this::buildClusterCacheData).collect(Collectors.toSet()));
    results.put(
        SERVER_GROUPS.getNs(),
        serverGroups.stream()
            .map(sg -> setServerGroupCacheData(toKeep, sg, loadDataStart))
            .collect(Collectors.toSet()));
    results.put(
        INSTANCES.getNs(),
        instances.stream().map(this::buildInstanceCacheData).collect(Collectors.toSet()));

    onDemandCacheData.forEach(this::processOnDemandCacheData);
    results.put(ON_DEMAND.getNs(), toKeep.values());

    log.debug(
        "Cache loaded for Cloud Foundry account {}, ({} sec)",
        accountName,
        (getInternalClock().millis() - loadDataStart) / 1000);
    return new DefaultCacheResult(results, Collections.singletonMap(ON_DEMAND.getNs(), toEvict));
  }

  @Override
  public boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandAgent.OnDemandType.ServerGroup)
        && cloudProvider.equals(CloudFoundryProvider.PROVIDER_ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    String account = Optional.ofNullable(data.get("account")).map(Object::toString).orElse(null);
    String region = Optional.ofNullable(data.get("region")).map(Object::toString).orElse(null);
    if (account == null || region == null) {
      return null;
    }
    CloudFoundrySpace space =
        this.getClient().getOrganizations().findSpaceByRegion(region).orElse(null);
    if (space == null) {
      return null;
    }
    String serverGroupName =
        Optional.ofNullable(data.get("serverGroupName")).map(Object::toString).orElse(null);
    if (serverGroupName == null) {
      return null;
    }
    CloudFoundryServerGroup serverGroup =
        this.getClient()
            .getApplications()
            .findServerGroupByNameAndSpaceId(serverGroupName, space.getId());
    if (serverGroup == null) {
      return null;
    }

    log.info("On Demand cache refresh triggered, waiting for Server group loadData to be called");
    CloudFoundryServerGroup cloudFoundryServerGroup =
        this.getClient()
            .getApplications()
            .findServerGroupByNameAndSpaceId(serverGroupName, space.getId());

    String serverGroupKey = Keys.getServerGroupKey(this.getAccount(), serverGroupName, region);
    Map<String, Collection<String>> evictions;
    DefaultCacheResult serverGroupCacheResults;

    if (cloudFoundryServerGroup != null) {
      Collection<CacheData> serverGroupCacheData =
          Collections.singleton(buildServerGroupCacheData(cloudFoundryServerGroup));

      serverGroupCacheResults =
          new DefaultCacheResult(
              Collections.singletonMap(SERVER_GROUPS.getNs(), serverGroupCacheData));

      providerCache.putCacheData(
          ON_DEMAND.getNs(),
          buildOnDemandCacheData(serverGroupKey, serverGroupCacheResults.getCacheResults()));
      evictions = Collections.emptyMap();
    } else {
      serverGroupCacheResults =
          new DefaultCacheResult(
              Collections.singletonMap(SERVER_GROUPS.getNs(), Collections.emptyList()));
      evictions =
          Collections.singletonMap(
              SERVER_GROUPS.getNs(),
              providerCache.filterIdentifiers(SERVER_GROUPS.getNs(), serverGroupKey));
    }

    return new OnDemandResult(getOnDemandAgentType(), serverGroupCacheResults, evictions);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> keys =
        providerCache.filterIdentifiers(
            ON_DEMAND.getNs(), Keys.getServerGroupKey(this.getAccount(), "*", "*"));
    return providerCache.getAll(ON_DEMAND.getNs(), keys, RelationshipCacheFilter.none()).stream()
        .map(
            it -> {
              String serverGroupId = it.getId();
              Map<String, String> details = Keys.parse(serverGroupId).orElse(emptyMap());
              Map<String, Object> attributes = it.getAttributes();

              return HashMap.of(
                      "id",
                      serverGroupId,
                      "details",
                      details,
                      "moniker",
                      convertOnDemandDetails(
                          singletonMap("serverGroupName", details.get("serverGroup"))),
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

  @Override
  public Moniker convertOnDemandDetails(Map<String, String> monikerData) {
    return Optional.ofNullable(monikerData)
        .flatMap(
            m ->
                Optional.ofNullable(m.get("serverGroupName"))
                    .map(
                        serverGroupName -> {
                          Names names = Names.parseName(serverGroupName);
                          return Moniker.builder()
                              .app(names.getApp())
                              .stack(names.getStack())
                              .detail(names.getDetail())
                              .cluster(names.getCluster())
                              .sequence(names.getSequence())
                              .build();
                        }))
        .orElse(null);
  }

  private CacheData setServerGroupCacheData(
      Map<String, CacheData> onDemandCacheDataToKeep,
      CloudFoundryServerGroup serverGroup,
      long start) {
    String account = this.getAccount();
    String key = Keys.getServerGroupKey(account, serverGroup.getName(), serverGroup.getRegion());
    CacheData sgCacheData = onDemandCacheDataToKeep.get(key);
    if (sgCacheData != null && (long) sgCacheData.getAttributes().get("cacheTime") > start) {
      Map<String, Collection<ResourceCacheData>> cacheResults =
          getCacheResultsFromCacheData(sgCacheData);
      onDemandCacheDataToKeep.remove(key);
      return cacheResults.get(SERVER_GROUPS.getNs()).stream().findFirst().orElse(null);
    } else {
      return buildServerGroupCacheData(serverGroup);
    }
  }

  private CacheData buildApplicationCacheData(CloudFoundryApplication app) {
    return new ResourceCacheData(
        Keys.getApplicationKey(app.getName()),
        cacheView(app),
        singletonMap(
            CLUSTERS.getNs(),
            app.getClusters().stream()
                .map(
                    cluster ->
                        Keys.getClusterKey(this.getAccountName(), app.getName(), cluster.getName()))
                .collect(toSet())));
  }

  private CacheData buildClusterCacheData(CloudFoundryCluster cluster) {
    String account = this.getAccount();
    return new ResourceCacheData(
        Keys.getClusterKey(account, cluster.getMoniker().getApp(), cluster.getName()),
        cacheView(cluster),
        singletonMap(
            SERVER_GROUPS.getNs(),
            cluster.getServerGroups().stream()
                .map(sg -> Keys.getServerGroupKey(account, sg.getName(), sg.getRegion()))
                .collect(toSet())));
  }

  private CacheData buildServerGroupCacheData(CloudFoundryServerGroup serverGroup) {
    String account = this.getAccount();
    return new ResourceCacheData(
        Keys.getServerGroupKey(account, serverGroup.getName(), serverGroup.getRegion()),
        cacheView(serverGroup),
        HashMap.<String, Collection<String>>of(
                INSTANCES.getNs(),
                serverGroup.getInstances().stream()
                    .map(inst -> Keys.getInstanceKey(account, inst.getName()))
                    .collect(toSet()),
                LOAD_BALANCERS.getNs(),
                serverGroup.getLoadBalancers().stream()
                    .map(
                        lb ->
                            Keys.getLoadBalancerKey(
                                account, lb, serverGroup.getSpace().getRegion()))
                    .collect(toSet()))
            .toJavaMap());
  }

  private CacheData buildInstanceCacheData(CloudFoundryInstance instance) {
    return new ResourceCacheData(
        Keys.getInstanceKey(this.getAccount(), instance.getName()),
        cacheView(instance),
        emptyMap());
  }
}
