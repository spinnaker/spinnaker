/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SERVER_GROUPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.AgentDataType.Authority;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.cats.provider.DefaultProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandResult;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.model.GoogleLabeledResource;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.names.GoogleLabeledResourceNamer;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class GoogleRegionalServerGroupCachingAgentTest {

  private static final NamingStrategy<GoogleLabeledResource> NAMER =
      new GoogleLabeledResourceNamer();

  private static final String ACCOUNT_NAME = "partypups";
  private static final String PROJECT = "myproject";
  private static final String REGION = "myregion";
  private static final String REGION_URL = "http://compute/regions/" + REGION;
  private static final String ZONE = REGION + "-myzone";

  private ObjectMapper objectMapper;

  @BeforeEach
  public void createTestObjects() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void loadData_attributesAndRelationships() {

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(
                instanceGroupManager("myServerGroup-prod-v001")
                    .setInstanceTemplate(
                        "http://compute/instanceTemplates/global/myInstanceTemplate"),
                instanceGroupManager("myOtherServerGroup-v003"))
            .setInstances(
                instance("myServerGroup-prod-v001-1111"),
                instance("myServerGroup-prod-v001-2222"),
                instance("myOtherServerGroup-v003-3333"),
                instance("myOtherServerGroup-v003-4444"))
            .setInstanceTemplates(
                new InstanceTemplate()
                    .setName("myInstanceTemplate")
                    .setProperties(
                        new InstanceProperties()
                            .setMetadata(
                                new Metadata()
                                    .setItems(
                                        ImmutableList.of(
                                            new Items()
                                                .setKey("load-balancer-names")
                                                .setValue(
                                                    "regionalLoadBalancer1,regionalLoadBalancer2"),
                                            new Items()
                                                .setKey("global-load-balancer-names")
                                                .setValue(
                                                    "globalLoadBalancer1,globalLoadBalancer2"))))))
            .create();

    Moniker moniker = moniker("myServerGroup-prod-v001");

    GoogleRegionalServerGroupCachingAgent cachingAgent = createCachingAgent(compute);
    CacheResult cacheResult = cachingAgent.loadData(inMemoryProviderCache());

    Collection<CacheData> applications = cacheResult.getCacheResults().get(APPLICATIONS.getNs());
    assertThat(applications)
        .extracting(app -> app.getAttributes().get("name"))
        .containsExactlyInAnyOrder("myServerGroup", "myOtherServerGroup");

    CacheData application = getNamedItem(applications, moniker.getApp());
    assertThat(application.getRelationships().get(CLUSTERS.getNs()))
        .containsExactly(clusterKey(moniker));
    assertThat(application.getRelationships().get(INSTANCES.getNs()))
        .containsExactlyInAnyOrder(
            instanceKey("myServerGroup-prod-v001-1111"),
            instanceKey("myServerGroup-prod-v001-2222"));

    Collection<CacheData> clusters = cacheResult.getCacheResults().get(CLUSTERS.getNs());
    assertThat(clusters)
        .extracting(cluster -> cluster.getAttributes().get("name"))
        .containsExactlyInAnyOrder("myServerGroup-prod", "myOtherServerGroup");

    CacheData cluster = getNamedItem(clusters, "myServerGroup-prod");
    assertThat(cluster.getAttributes())
        .containsOnly(
            entry("name", "myServerGroup-prod"),
            entry("accountName", ACCOUNT_NAME),
            entry("moniker", moniker));
    assertThat(cluster.getRelationships().get(APPLICATIONS.getNs()))
        .containsExactly(applicationKey(moniker));
    assertThat(cluster.getRelationships().get(SERVER_GROUPS.getNs()))
        .containsExactly(serverGroupKey("myServerGroup-prod-v001"));
    assertThat(application.getRelationships().get(INSTANCES.getNs()))
        .containsExactlyInAnyOrder(
            instanceKey("myServerGroup-prod-v001-1111"),
            instanceKey("myServerGroup-prod-v001-2222"));

    ImmutableList<String> expectedLoadBalancerKeys =
        ImmutableList.of(
            Keys.getLoadBalancerKey(REGION, ACCOUNT_NAME, "regionalLoadBalancer1"),
            Keys.getLoadBalancerKey(REGION, ACCOUNT_NAME, "regionalLoadBalancer2"),
            Keys.getLoadBalancerKey("global", ACCOUNT_NAME, "globalLoadBalancer1"),
            Keys.getLoadBalancerKey("global", ACCOUNT_NAME, "globalLoadBalancer2"));

    Collection<CacheData> loadBalancers = cacheResult.getCacheResults().get(LOAD_BALANCERS.getNs());
    assertThat(loadBalancers)
        .extracting(CacheData::getId)
        .containsExactlyInAnyOrderElementsOf(expectedLoadBalancerKeys);
    for (CacheData loadBalancer : loadBalancers) {
      assertThat(loadBalancer.getRelationships().get(SERVER_GROUPS.getNs()))
          .containsExactly(serverGroupKey("myServerGroup-prod-v001"));
    }

    Collection<CacheData> serverGroups = cacheResult.getCacheResults().get(SERVER_GROUPS.getNs());
    assertThat(serverGroups)
        .extracting(serverGroup -> serverGroup.getAttributes().get("name"))
        .containsExactlyInAnyOrder("myServerGroup-prod-v001", "myOtherServerGroup-v003");
    CacheData serverGroup = getNamedItem(serverGroups, "myServerGroup-prod-v001");
    // serverGroup's attributes are tested in the variety of methods above, so we'll only test the
    // relationships
    assertThat(serverGroup.getRelationships().get(APPLICATIONS.getNs()))
        .containsExactly(applicationKey(moniker));
    assertThat(serverGroup.getRelationships().get(CLUSTERS.getNs()))
        .containsExactly(clusterKey(moniker));
    assertThat(serverGroup.getRelationships().get(INSTANCES.getNs()))
        .containsExactlyInAnyOrder(
            instanceKey("myServerGroup-prod-v001-1111"),
            instanceKey("myServerGroup-prod-v001-2222"));
    assertThat(serverGroup.getRelationships().get(LOAD_BALANCERS.getNs()))
        .containsExactlyInAnyOrderElementsOf(expectedLoadBalancerKeys);
  }

  @Test
  void loadData_existingOnDemandData() throws JsonProcessingException {

    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(
                instanceGroupManager("cachedInPastUnprocessed-v001"),
                instanceGroupManager("cachedInPastProcessed-v002"),
                instanceGroupManager("cachedInFutureUnprocessedNoData-v003"),
                instanceGroupManager("cachedInFutureUnprocessedData-v004"),
                instanceGroupManager("cachedInFutureProcessedNoData-v005"),
                instanceGroupManager("cachedInFutureProcessedData-v006"))
            .create();

    long timeInPast = System.currentTimeMillis() - 100000;
    long timeInFuture = System.currentTimeMillis() + 100000;

    ProviderCache providerCache = inMemoryProviderCache();
    providerCache.putCacheData(
        ON_DEMAND.getNs(),
        new DefaultCacheData(
            serverGroupKey("cachedInPastUnprocessed-v001"),
            ImmutableMap.of("cacheTime", timeInPast, "processedCount", 0),
            ImmutableMap.of()));
    providerCache.putCacheData(
        ON_DEMAND.getNs(),
        new DefaultCacheData(
            serverGroupKey("cachedInPastProcessed-v002"),
            ImmutableMap.of("cacheTime", timeInPast, "processedCount", 1),
            ImmutableMap.of()));
    providerCache.putCacheData(
        ON_DEMAND.getNs(),
        new DefaultCacheData(
            serverGroupKey("cachedInFutureUnprocessedNoData-v003"),
            ImmutableMap.of("cacheTime", timeInFuture, "processedCount", 0, "cacheResults", "{}"),
            ImmutableMap.of()));
    providerCache.putCacheData(
        ON_DEMAND.getNs(),
        new DefaultCacheData(
            serverGroupKey("cachedInFutureUnprocessedData-v004"),
            ImmutableMap.of(
                "cacheTime",
                timeInFuture,
                "processedCount",
                0,
                "cacheResults",
                serverGroupCacheData("cachedInFutureUnprocessedData-v004")),
            ImmutableMap.of()));
    providerCache.putCacheData(
        ON_DEMAND.getNs(),
        new DefaultCacheData(
            serverGroupKey("cachedInFutureProcessedNoData-v005"),
            ImmutableMap.of("cacheTime", timeInFuture, "processedCount", 1, "cacheResults", "{}"),
            ImmutableMap.of()));
    providerCache.putCacheData(
        ON_DEMAND.getNs(),
        new DefaultCacheData(
            serverGroupKey("cachedInFutureProcessedData-v006"),
            ImmutableMap.of(
                "cacheTime",
                timeInFuture,
                "processedCount",
                1,
                "cacheResults",
                serverGroupCacheData("cachedInFutureProcessedData-v006")),
            ImmutableMap.of()));
    GoogleRegionalServerGroupCachingAgent cachingAgent = createCachingAgent(compute);

    CacheResult cacheResult = cachingAgent.loadData(providerCache);

    // The already-processed item that was still lying around from a previous run should get
    // evicted.
    assertThat(cacheResult.getEvictions().get(ON_DEMAND.getNs()))
        .containsExactlyInAnyOrder(serverGroupKey("cachedInPastProcessed-v002"));

    // These things weren't handled. The first was ignored because it was created before our caching
    // run started. The second and third were ignored because they didn't have any data attached.
    // They'll both have their "processedCount" incremented and get cleared in the next caching run
    // (since they will then already-processed items from the past, as above).
    Collection<CacheData> onDemandData = cacheResult.getCacheResults().get(ON_DEMAND.getNs());
    assertThat(onDemandData)
        .extracting(CacheData::getId)
        .containsExactlyInAnyOrder(
            serverGroupKey("cachedInPastUnprocessed-v001"),
            serverGroupKey("cachedInFutureUnprocessedNoData-v003"),
            serverGroupKey("cachedInFutureProcessedNoData-v005"));
    CacheData cachedInPastUnprocessed =
        getKeyedItem(onDemandData, serverGroupKey("cachedInPastUnprocessed-v001"));
    assertThat(cachedInPastUnprocessed.getAttributes()).contains(entry("processedCount", 1));
    CacheData cachedInFutureUnprocessedNoData =
        getKeyedItem(onDemandData, serverGroupKey("cachedInFutureUnprocessedNoData-v003"));
    assertThat(cachedInFutureUnprocessedNoData.getAttributes())
        .contains(entry("processedCount", 1));
    CacheData cachedInFutureProcessed =
        getKeyedItem(onDemandData, serverGroupKey("cachedInFutureProcessedNoData-v005"));
    assertThat(cachedInFutureProcessed.getAttributes()).contains(entry("processedCount", 2));

    // Finally, these items, which contain on-demand data that was inserted in the middle of our
    // caching run, should have their cache results copied from the on-demand data. Further
    // validation of how this works is in the test below.
    CacheData cachedInFutureUnprocessedData =
        getKeyedItem(
            cacheResult.getCacheResults().get(SERVER_GROUPS.getNs()),
            serverGroupKey("cachedInFutureUnprocessedData-v004"));
    assertThat(cachedInFutureUnprocessedData.getAttributes()).containsKeys("copiedFromCacheData");
    CacheData cachedInFutureProcessedData =
        getKeyedItem(
            cacheResult.getCacheResults().get(SERVER_GROUPS.getNs()),
            serverGroupKey("cachedInFutureProcessedData-v006"));
    assertThat(cachedInFutureProcessedData.getAttributes()).containsKeys("copiedFromCacheData");
  }

  private String serverGroupCacheData(String serverGroupName) throws JsonProcessingException {
    return objectMapper.writeValueAsString(
        ImmutableMap.of(
            SERVER_GROUPS.getNs(),
            ImmutableList.of(
                new DefaultCacheData(
                    serverGroupKey(serverGroupName),
                    ImmutableMap.of("copiedFromCacheData", true),
                    ImmutableMap.of()))));
  }

  @Test
  void loadData_copyFromOnDemandBehavior() throws Exception {
    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(instanceGroupManager("overwritten-v001"))
            .setInstances(instance("overwritten-v001-abcd"))
            .create();

    long timeInFuture = System.currentTimeMillis() + 100000;

    ImmutableMap<String, Collection<String>> instanceRelationshipFromOnDemandCache =
        ImmutableMap.of(INSTANCES.getNs(), ImmutableList.of(instanceKey("overwritten-v001-efgh")));

    Moniker moniker = moniker("overwritten-v001");

    ProviderCache providerCache = inMemoryProviderCache();
    providerCache.putCacheData(
        ON_DEMAND.getNs(),
        new DefaultCacheData(
            serverGroupKey("overwritten-v001"),
            ImmutableMap.of(
                "cacheTime",
                timeInFuture,
                "processedCount",
                0,
                "cacheResults",
                objectMapper.writeValueAsString(
                    ImmutableMap.of(
                        APPLICATIONS.getNs(),
                        ImmutableList.of(
                            new DefaultCacheData(
                                applicationKey(moniker),
                                ImmutableMap.of("onDemandAttribute", "application"),
                                instanceRelationshipFromOnDemandCache)),
                        SERVER_GROUPS.getNs(),
                        ImmutableList.of(
                            new DefaultCacheData(
                                serverGroupKey("overwritten-v001"),
                                ImmutableMap.of("onDemandAttribute", "serverGroup"),
                                instanceRelationshipFromOnDemandCache))))),
            ImmutableMap.of()));
    GoogleRegionalServerGroupCachingAgent cachingAgent = createCachingAgent(compute);

    CacheResult cacheResult = cachingAgent.loadData(providerCache);

    // This item, which was put into on-demand during our caching run, and which contains some valid
    // data, should get copied over to the main results. For application, cluster, and load balancer
    // data, the previous attributes get replaced, but relationships get merged.
    CacheData application =
        getKeyedItem(
            cacheResult.getCacheResults().get(APPLICATIONS.getNs()), applicationKey(moniker));
    // Verify that keys from the loaded application are wiped out in favor of the keys from the
    // on-demand cache
    assertThat(application.getAttributes()).doesNotContainKey("name");
    assertThat(application.getAttributes().get("onDemandAttribute")).isEqualTo("application");
    // -abcd comes from the original cache, -efgh comes from the on-demand cache
    assertThat(application.getRelationships().get(INSTANCES.getNs()))
        .containsExactlyInAnyOrder(
            instanceKey("overwritten-v001-abcd"), instanceKey("overwritten-v001-efgh"));

    // The cluster didn't have an entry in the on-demand cache, so it should just have the data we
    // loaded from GCE.
    CacheData cluster =
        getKeyedItem(cacheResult.getCacheResults().get(CLUSTERS.getNs()), clusterKey(moniker));
    assertThat(cluster.getAttributes().get("name")).isEqualTo("overwritten");
    assertThat(cluster.getRelationships().get(INSTANCES.getNs()))
        .containsExactlyInAnyOrder(instanceKey("overwritten-v001-abcd"));

    // Unlike the application, cluster, and load balancers, the server group does NOT get its
    // relationships merged. It just uses the relationships from the on-demand server group.
    // But why, you ask? ¯\_(ツ)_/¯
    CacheData serverGroup =
        getKeyedItem(
            cacheResult.getCacheResults().get(SERVER_GROUPS.getNs()),
            serverGroupKey("overwritten-v001"));
    // Verify that keys from the loaded server group are wiped out in favor of the keys from the
    // on-demand cache
    assertThat(serverGroup.getAttributes()).doesNotContainKey("name");
    assertThat(serverGroup.getAttributes().get("onDemandAttribute")).isEqualTo("serverGroup");
    assertThat(serverGroup.getRelationships().get(INSTANCES.getNs()))
        .containsExactlyInAnyOrder(instanceKey("overwritten-v001-efgh"));
  }

  @Test
  void pendingOnDemandRequests() {
    ProviderCache providerCache = inMemoryProviderCache();
    String applicationKey = Keys.getApplicationKey("application");
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(applicationKey));
    String clusterKey = Keys.getClusterKey(ACCOUNT_NAME, "cluster", "cluster");
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(clusterKey));
    String loadBalancerKey = Keys.getLoadBalancerKey(REGION, ACCOUNT_NAME, "loadBalancer");
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(loadBalancerKey));
    String zonalServerGroupKey =
        Keys.getServerGroupKey("mig1-v001", "mig1", ACCOUNT_NAME, REGION, ZONE);
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(zonalServerGroupKey));
    String ownedServerGroupKey = Keys.getServerGroupKey("mig2-v002", "mig2", ACCOUNT_NAME, REGION);
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(ownedServerGroupKey));
    String differentAccountServerGroupKey =
        Keys.getServerGroupKey("mig1-v001", "mig1", "someOtherAccount", REGION, ZONE);
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(differentAccountServerGroupKey));
    String differentRegionServerGroupKey =
        Keys.getServerGroupKey("mig1-v001", "mig1", ACCOUNT_NAME, "someOtherRegion", ZONE);
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(differentRegionServerGroupKey));

    GoogleRegionalServerGroupCachingAgent cachingAgent =
        createCachingAgent(new StubComputeFactory().create());
    Collection<Map> pendingRequests = cachingAgent.pendingOnDemandRequests(providerCache);

    assertThat(pendingRequests).hasSize(1);
    assertThat(getOnlyElement(pendingRequests))
        .contains(entry("details", Keys.parse(ownedServerGroupKey)));
  }

  @Test
  void pendingOnDemandRequests_attributes() {
    ProviderCache providerCache = inMemoryProviderCache();
    String key = Keys.getServerGroupKey("mig1-v001", "mig1", ACCOUNT_NAME, REGION);
    providerCache.putCacheData(
        ON_DEMAND.getNs(),
        cacheData(
            key,
            ImmutableMap.of(
                "moniker", moniker("mig1-v001"),
                "cacheTime", 12345,
                "processedCount", 3,
                "processedTime", 67890)));

    GoogleRegionalServerGroupCachingAgent cachingAgent =
        createCachingAgent(new StubComputeFactory().create());
    Collection<Map> pendingRequests = cachingAgent.pendingOnDemandRequests(providerCache);

    assertThat(pendingRequests).hasSize(1);
    assertThat(getOnlyElement(pendingRequests))
        .containsOnly(
            entry("details", Keys.parse(key)),
            entry("moniker", moniker("mig1-v001")),
            entry("cacheTime", 12345),
            entry("processedCount", 3),
            entry("processedTime", 67890));
  }

  @Test
  void handle_serverGroupDoesNotExistAndIsNotInCache() {
    ProviderCache providerCache = inMemoryProviderCache();
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(serverGroupKey("myServerGroup")));

    Compute compute = new StubComputeFactory().create();
    GoogleRegionalServerGroupCachingAgent cachingAgent = createCachingAgent(compute);
    OnDemandResult result =
        cachingAgent.handle(
            providerCache,
            ImmutableMap.of(
                "serverGroupName", "myServerGroup", "account", ACCOUNT_NAME, "region", REGION));

    assertThat(providerCache.get(ON_DEMAND.getNs(), serverGroupKey("myServerGroup"))).isNull();

    assertThat(result.getSourceAgentType()).isEqualTo(cachingAgent.getOnDemandAgentType());
    assertThat(result.getEvictions())
        .containsExactly(
            entry(SERVER_GROUPS.getNs(), ImmutableList.of(serverGroupKey("myServerGroup"))));
    assertThat(result.getAuthoritativeTypes()).isEmpty();
    assertThat(result.getCacheResult().getCacheResults().values()).allMatch(Collection::isEmpty);
    assertThat(result.getCacheResult().getEvictions()).isEmpty();
  }

  @Test
  void handle_serverGroupDoesNotExistButIsInCache() {
    ProviderCache providerCache = inMemoryProviderCache();
    providerCache.putCacheData(SERVER_GROUPS.getNs(), cacheData(serverGroupKey("myServerGroup")));
    providerCache.putCacheData(ON_DEMAND.getNs(), cacheData(serverGroupKey("myServerGroup")));

    Compute compute = new StubComputeFactory().create();
    GoogleRegionalServerGroupCachingAgent cachingAgent = createCachingAgent(compute);
    OnDemandResult result =
        cachingAgent.handle(
            providerCache,
            ImmutableMap.of(
                "serverGroupName", "myServerGroup", "account", ACCOUNT_NAME, "region", REGION));

    // It evicts the server group from ON_DEMAND, but not from SERVER_GROUPS
    assertThat(providerCache.get(ON_DEMAND.getNs(), serverGroupKey("myServerGroup"))).isNull();
    assertThat(providerCache.get(SERVER_GROUPS.getNs(), serverGroupKey("myServerGroup")))
        .isNotNull();

    assertThat(result.getSourceAgentType()).isEqualTo(cachingAgent.getOnDemandAgentType());
    assertThat(result.getEvictions())
        .containsExactly(
            entry(SERVER_GROUPS.getNs(), ImmutableList.of(serverGroupKey("myServerGroup"))));
    assertThat(result.getAuthoritativeTypes()).isEmpty();
    assertThat(result.getCacheResult().getCacheResults().values()).allMatch(Collection::isEmpty);
    assertThat(result.getCacheResult().getEvictions()).isEmpty();
  }

  @Test
  void handle_serverGroupExists() throws IOException {
    Compute compute =
        new StubComputeFactory()
            .setInstanceGroupManagers(
                instanceGroupManager("myservergroup-v001")
                    .setInstanceTemplate(
                        "http://compute/global/instanceTemplates/my-instance-template"))
            .create();
    GoogleRegionalServerGroupCachingAgent cachingAgent = createCachingAgent(compute);
    ProviderCache providerCache = inMemoryProviderCache();
    OnDemandResult result =
        cachingAgent.handle(
            providerCache,
            ImmutableMap.of(
                "serverGroupName", "myservergroup-v001",
                "account", ACCOUNT_NAME,
                "region", REGION));

    CacheData cacheData =
        providerCache.get(ON_DEMAND.getNs(), serverGroupKey("myservergroup-v001"));
    Map<String, Collection<DefaultCacheData>> cacheResults =
        objectMapper.readValue(
            (String) cacheData.getAttributes().get("cacheResults"),
            new TypeReference<Map<String, Collection<DefaultCacheData>>>() {});
    assertThat(cacheResults.get(SERVER_GROUPS.getNs()))
        .extracting(data -> data.getAttributes().get("name"))
        .containsExactly("myservergroup-v001");

    assertThat(result.getSourceAgentType()).isEqualTo(cachingAgent.getOnDemandAgentType());
    assertThat(result.getEvictions().values()).allMatch(Collection::isEmpty);
    assertThat(result.getAuthoritativeTypes()).isEmpty();
    assertThat(result.getCacheResult().getCacheResults().get(SERVER_GROUPS.getNs()))
        .extracting(data -> data.getAttributes().get("name"))
        .containsExactly("myservergroup-v001");
    assertThat(result.getCacheResult().getEvictions()).isEmpty();
  }

  @Test
  void pendingOnDemandRequestsPersistAcrossOneCachingCycle() {

    GoogleRegionalServerGroupCachingAgent cachingAgent =
        createCachingAgent(
            new StubComputeFactory()
                .setInstanceGroupManagers(instanceGroupManager("myservergroup-v001"))
                .create());

    ProviderCache providerCache = inMemoryProviderCache();
    cachingAgent.handle(
        providerCache,
        ImmutableMap.of(
            "serverGroupName", "myservergroup-v001", "account", ACCOUNT_NAME, "region", REGION));

    Collection<Map> pendingRequests = cachingAgent.pendingOnDemandRequests(providerCache);
    assertThat(pendingRequests).hasSize(1);
    assertThat(pendingRequests).extracting(map -> map.get("processedCount")).containsExactly(0);

    CacheResult cacheResult = cachingAgent.loadData(providerCache);
    storeResultInProviderCache(cacheResult, providerCache, cachingAgent);

    pendingRequests = cachingAgent.pendingOnDemandRequests(providerCache);
    assertThat(pendingRequests).hasSize(1);
    assertThat(pendingRequests).extracting(map -> map.get("processedCount")).containsExactly(1);

    cacheResult = cachingAgent.loadData(providerCache);
    storeResultInProviderCache(cacheResult, providerCache, cachingAgent);

    pendingRequests = cachingAgent.pendingOnDemandRequests(providerCache);
    assertThat(pendingRequests).isEmpty();
  }

  private static void storeResultInProviderCache(
      CacheResult cacheResult,
      ProviderCache providerCache,
      GoogleRegionalServerGroupCachingAgent cachingAgent) {

    ImmutableSet<String> authoritativeTypes =
        cachingAgent.getProvidedDataTypes().stream()
            .filter(type -> type.getAuthority().equals(Authority.AUTHORITATIVE))
            .map(AgentDataType::getTypeName)
            .collect(toImmutableSet());
    providerCache.putCacheResult(cachingAgent.getAgentType(), authoritativeTypes, cacheResult);
  }

  private static CacheData cacheData(String key) {
    // InMemoryCache will ignore this if it doesn't have at least one attribute
    return new DefaultCacheData(key, ImmutableMap.of("attribute", "value"), ImmutableMap.of());
  }

  private static CacheData cacheData(String key, Map<String, Object> attributes) {
    return new DefaultCacheData(key, attributes, ImmutableMap.of());
  }

  private static CacheData getNamedItem(Collection<CacheData> items, String name) {
    return items.stream()
        .filter(item -> item.getAttributes().get("name").equals(name))
        .findAny()
        .orElseThrow(
            () -> new AssertionError(String.format("Couldn't find item named '%s'", name)));
  }

  private static CacheData getKeyedItem(Collection<CacheData> items, String key) {
    return items.stream()
        .filter(item -> item.getId().equals(key))
        .findAny()
        .orElseThrow(
            () -> new AssertionError(String.format("Couldn't find item with key '%s'", key)));
  }

  public static GoogleRegionalServerGroupCachingAgent createCachingAgent(Compute compute) {
    return new GoogleRegionalServerGroupCachingAgent(
        new GoogleNamedAccountCredentials.Builder()
            .project(PROJECT)
            .name(ACCOUNT_NAME)
            .compute(compute)
            .regionToZonesMap(ImmutableMap.of(REGION, ImmutableList.of(ZONE)))
            .build(),
        new GoogleComputeApiFactory(
            new GoogleOperationPoller(),
            new DefaultRegistry(),
            "user-agent",
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())),
        new DefaultRegistry(),
        REGION,
        new ObjectMapper());
  }

  private static InstanceGroupManager instanceGroupManager(String name) {
    return new InstanceGroupManager()
        .setName(name)
        .setBaseInstanceName(name + "-")
        .setRegion(REGION_URL);
  }

  private static Instance instance(String name) {
    return new Instance().setName(name).setZone(ZONE);
  }

  private static Moniker moniker(String serverGroupName) {
    return NAMER.deriveMoniker(new GoogleServerGroup(serverGroupName));
  }

  private static String applicationKey(Moniker moniker) {
    return Keys.getApplicationKey(moniker.getApp());
  }

  private static String clusterKey(Moniker moniker) {
    return Keys.getClusterKey(ACCOUNT_NAME, moniker.getApp(), moniker.getCluster());
  }

  private static String serverGroupKey(String serverGroupName) {
    Moniker moniker = moniker(serverGroupName);
    return Keys.getServerGroupKey(serverGroupName, moniker.getCluster(), ACCOUNT_NAME, REGION);
  }

  private static String instanceKey(String instanceName) {
    return Keys.getInstanceKey(ACCOUNT_NAME, REGION, instanceName);
  }

  private static DefaultProviderCache inMemoryProviderCache() {
    return new DefaultProviderCache(new InMemoryCache());
  }
}
