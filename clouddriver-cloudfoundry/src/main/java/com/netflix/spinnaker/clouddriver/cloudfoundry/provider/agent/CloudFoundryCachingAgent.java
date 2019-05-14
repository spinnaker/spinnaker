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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.ResourceCacheData;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.moniker.Moniker;
import io.vavr.collection.HashMap;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class CloudFoundryCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {
  private final String providerName = CloudFoundryProvider.class.getName();
  private final Collection<AgentDataType> providedDataTypes =
      Arrays.asList(
          AUTHORITATIVE.forType(APPLICATIONS.getNs()),
          AUTHORITATIVE.forType(CLUSTERS.getNs()),
          AUTHORITATIVE.forType(SERVER_GROUPS.getNs()),
          AUTHORITATIVE.forType(INSTANCES.getNs()),
          AUTHORITATIVE.forType(LOAD_BALANCERS.getNs()));

  private static final ObjectMapper cacheViewMapper =
      new ObjectMapper().disable(MapperFeature.DEFAULT_VIEW_INCLUSION);

  private final String account;
  private final OnDemandMetricsSupport metricsSupport;
  private final CloudFoundryClient client;
  private final Clock internalClock;

  public CloudFoundryCachingAgent(String account, CloudFoundryClient client, Registry registry) {
    this(account, client, registry, Clock.systemDefaultZone());
  }

  public CloudFoundryCachingAgent(
      String account, CloudFoundryClient client, Registry registry, Clock internalClock) {
    this.account = account;
    this.client = client;
    cacheViewMapper.setConfig(cacheViewMapper.getSerializationConfig().withView(Views.Cache.class));
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, CloudFoundryProvider.PROVIDER_ID + ":" + OnDemandType.ServerGroup);
    this.internalClock = internalClock;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    long loadDataStart = internalClock.millis();
    String accountName = getAccountName();
    log.info("Caching all resources in Cloud Foundry account " + accountName);

    List<CloudFoundryApplication> apps = client.getApplications().all();
    List<CloudFoundryLoadBalancer> loadBalancers = client.getRoutes().all();

    Map<String, Collection<CacheData>> results =
        HashMap.<String, Collection<CacheData>>of(
                LOAD_BALANCERS.getNs(),
                    loadBalancers.stream()
                        .map(
                            lb ->
                                new ResourceCacheData(
                                    Keys.getLoadBalancerKey(accountName, lb),
                                    cacheView(lb),
                                    singletonMap(
                                        SERVER_GROUPS.getNs(),
                                        lb.getServerGroups().stream()
                                            .map(
                                                sg ->
                                                    Keys.getServerGroupKey(
                                                        accountName, sg.getName(), sg.getRegion()))
                                            .collect(toSet()))))
                        .collect(toSet()),
                APPLICATIONS.getNs(),
                    apps.stream()
                        .map(
                            app ->
                                new ResourceCacheData(
                                    Keys.getApplicationKey(app.getName()),
                                    cacheView(app),
                                    singletonMap(
                                        CLUSTERS.getNs(),
                                        app.getClusters().stream()
                                            .map(
                                                cluster ->
                                                    Keys.getClusterKey(
                                                        accountName,
                                                        app.getName(),
                                                        cluster.getName()))
                                            .collect(toSet()))))
                        .collect(toSet()),
                CLUSTERS.getNs(),
                    apps.stream()
                        .flatMap(
                            app ->
                                app.getClusters().stream()
                                    .map(
                                        cluster ->
                                            new ResourceCacheData(
                                                Keys.getClusterKey(
                                                    accountName, app.getName(), cluster.getName()),
                                                cacheView(cluster),
                                                singletonMap(
                                                    SERVER_GROUPS.getNs(),
                                                    cluster.getServerGroups().stream()
                                                        .map(
                                                            sg ->
                                                                Keys.getServerGroupKey(
                                                                    accountName,
                                                                    sg.getName(),
                                                                    sg.getRegion()))
                                                        .collect(toSet())))))
                        .collect(toSet()),
                SERVER_GROUPS.getNs(),
                    apps.stream()
                        .flatMap(
                            app ->
                                app.getClusters().stream()
                                    .flatMap(
                                        cluster ->
                                            cluster.getServerGroups().stream()
                                                .map(
                                                    serverGroup -> {
                                                      Map<String, Collection<String>>
                                                          relationships =
                                                              HashMap
                                                                  .<String, Collection<String>>of(
                                                                      INSTANCES.getNs(),
                                                                          serverGroup.getInstances()
                                                                              .stream()
                                                                              .map(
                                                                                  inst ->
                                                                                      Keys
                                                                                          .getInstanceKey(
                                                                                              accountName,
                                                                                              inst
                                                                                                  .getName()))
                                                                              .collect(toSet()),
                                                                      LOAD_BALANCERS.getNs(),
                                                                          loadBalancers.stream()
                                                                              .filter(
                                                                                  lb ->
                                                                                      lb
                                                                                          .getServerGroups()
                                                                                          .stream()
                                                                                          .anyMatch(
                                                                                              lbSg ->
                                                                                                  lbSg.getName()
                                                                                                          .equals(
                                                                                                              serverGroup
                                                                                                                  .getName())
                                                                                                      && lbSg.getRegion()
                                                                                                          .equals(
                                                                                                              serverGroup
                                                                                                                  .getRegion())
                                                                                                      && lbSg.getAccount()
                                                                                                          .equals(
                                                                                                              accountName)))
                                                                              .map(
                                                                                  lb ->
                                                                                      Keys
                                                                                          .getLoadBalancerKey(
                                                                                              accountName,
                                                                                              lb))
                                                                              .collect(toSet()))
                                                                  .toJavaMap();

                                                      return new ResourceCacheData(
                                                          Keys.getServerGroupKey(
                                                              accountName,
                                                              serverGroup.getName(),
                                                              serverGroup.getRegion()),
                                                          cacheView(serverGroup),
                                                          relationships);
                                                    })))
                        .collect(toSet()),
                INSTANCES.getNs(),
                    apps.stream()
                        .flatMap(app -> app.getClusters().stream())
                        .flatMap(cluster -> cluster.getServerGroups().stream())
                        .flatMap(serverGroup -> serverGroup.getInstances().stream())
                        .map(
                            inst ->
                                new ResourceCacheData(
                                    Keys.getInstanceKey(accountName, inst.getName()),
                                    cacheView(inst),
                                    emptyMap()))
                        .collect(toSet()))
            .toJavaMap();

    Collection<String> pendingOnDemandRequestKeys =
        providerCache.filterIdentifiers(
            ON_DEMAND.getNs(), Keys.getServerGroupKey(account, "*", "*"));

    Collection<CacheData> allCacheData =
        providerCache.getAll(ON_DEMAND.getNs(), pendingOnDemandRequestKeys);
    allCacheData.forEach(
        cacheData -> {
          Map<String, Object> attributes = cacheData.getAttributes();
          attributes.put("processedTime", loadDataStart);
          attributes.put(
              "processedCount", (Integer) attributes.getOrDefault("processedCount", 0) + 1);
        });
    results.put(ON_DEMAND.getNs(), allCacheData);

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

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  @Override
  public boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandAgent.OnDemandType.ServerGroup)
        && cloudProvider.equals(CloudFoundryProvider.PROVIDER_ID);
  }

  @Override
  public OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    String region = Optional.ofNullable(data.get("region")).map(Object::toString).orElse(null);
    if (region == null) {
      return null;
    }
    CloudFoundrySpace space = client.getOrganizations().findSpaceByRegion(region).orElse(null);
    if (space == null) {
      return null;
    }
    String serverGroupName =
        Optional.ofNullable(data.get("serverGroupName")).map(Object::toString).orElse(null);
    if (serverGroupName == null) {
      return null;
    }
    CloudFoundryServerGroup serverGroup =
        client.getApplications().findServerGroupByNameAndSpaceId(serverGroupName, space.getId());
    if (serverGroup == null) {
      return null;
    }

    log.info("On Demand cache refresh triggered, waiting for loadData to be called");

    CacheData cacheData =
        new DefaultCacheData(
            Keys.getServerGroupKey(account, serverGroupName, region),
            (int) TimeUnit.MINUTES.toSeconds(10), // ttl
            HashMap.<String, Object>of("cacheTime", Date.from(internalClock.instant())).toJavaMap(),
            emptyMap(),
            internalClock);
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData);
    return new OnDemandResult(
        getOnDemandAgentType(),
        new DefaultCacheResult(singletonMap(ON_DEMAND.getNs(), Collections.singleton(cacheData))),
        emptyMap());
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> keys =
        providerCache.filterIdentifiers(
            ON_DEMAND.getNs(), Keys.getServerGroupKey(account, "*", "*"));
    return providerCache.getAll(ON_DEMAND.getNs(), keys, RelationshipCacheFilter.none()).stream()
        .map(
            it -> {
              String serverGroupId = it.getId();
              Map<String, String> details = Keys.parse(serverGroupId).orElse(emptyMap());
              Map<String, Object> attributes = it.getAttributes();

              return HashMap.of(
                      "id", serverGroupId,
                      "details", details,
                      "moniker",
                          convertOnDemandDetails(
                              singletonMap("serverGroupName", details.get("serverGroup"))),
                      "cacheTime", attributes.get("cacheTime"),
                      "cacheExpiry", attributes.get("cacheExpiry"),
                      "processedCount", attributes.get("processedCount"),
                      "processedTime", attributes.get("processedTime"))
                  .toJavaMap();
            })
        .collect(toSet());
  }

  @Override
  public Moniker convertOnDemandDetails(Map<String, String> monikerData) {
    return Optional.ofNullable(monikerData)
        .map(
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
                        })
                    .orElse(null))
        .orElse(null);
  }

  /**
   * Serialize just enough data to be able to reconstitute the model fully if its relationships are
   * also deserialized.
   */
  // Visible for testing
  static Map<String, Object> cacheView(Object o) {
    return cacheViewMapper.convertValue(o, new TypeReference<Map<String, Object>>() {});
  }
}
