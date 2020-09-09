package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryServerGroupCachingAgent.cacheView;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.ResourceCacheData;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Organizations;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Routes;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudFoundryLoadBalancerCachingAgentTest {
  private Instant now = Instant.now();
  private String accountName = "account";
  private ObjectMapper objectMapper =
      new ObjectMapper().disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
  private CloudFoundryClient cloudFoundryClient = mock(CloudFoundryClient.class);
  private Registry registry = mock(Registry.class);
  private final Clock internalClock = Clock.fixed(now, ZoneId.systemDefault());
  private CloudFoundryCredentials credentials = mock(CloudFoundryCredentials.class);
  private CloudFoundryLoadBalancerCachingAgent cloudFoundryLoadBalancerCachingAgent =
      new CloudFoundryLoadBalancerCachingAgent(credentials, registry);
  private ProviderCache mockProviderCache = mock(ProviderCache.class);
  private String spaceId = "space-guid";
  private String spaceName = "space";
  private String orgId = "org-guid";
  private String orgName = "org";
  private CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder()
          .id(spaceId)
          .name(spaceName)
          .organization(CloudFoundryOrganization.builder().id(orgId).name(orgName).build())
          .build();

  @BeforeEach
  void before() {
    when(credentials.getClient()).thenReturn(cloudFoundryClient);
    when(credentials.getName()).thenReturn(accountName);
  }

  @Test
  void handleShouldReturnNullWhenAccountDoesNotMatch() {
    Map<String, Object> data = HashMap.<String, Object>of("account", "other-account").toJavaMap();

    OnDemandAgent.OnDemandResult result =
        cloudFoundryLoadBalancerCachingAgent.handle(mockProviderCache, data);
    assertThat(result).isNull();
  }

  @Test
  void handleShouldReturnNullWhenRegionIsUnspecified() {
    OnDemandAgent.OnDemandResult result =
        cloudFoundryLoadBalancerCachingAgent.handle(mockProviderCache, emptyMap());

    assertThat(result).isNull();
  }

  @Test
  void handleShouldReturnNullWhenRegionDoesNotExist() {
    String region = "org > space";
    Map<String, Object> data =
        HashMap.<String, Object>of(
                "account", accountName,
                "region", region,
                "loadBalancerName", "loadBalancerName")
            .toJavaMap();

    Organizations organizations = mock(Organizations.class);
    when(organizations.findSpaceByRegion(any())).thenReturn(Optional.empty());

    when(cloudFoundryClient.getOrganizations()).thenReturn(organizations);

    OnDemandAgent.OnDemandResult result =
        cloudFoundryLoadBalancerCachingAgent.handle(mockProviderCache, data);
    assertThat(result).isNull();
    verify(organizations).findSpaceByRegion(eq(region));
  }

  @Test
  void handleShouldReturnNullWhenLoadBalancerNameIsUnspecified() {
    String region = "org > space";
    Map<String, Object> data =
        HashMap.<String, Object>of(
                "account", accountName,
                "region", region)
            .toJavaMap();

    Organizations organizations = mock(Organizations.class);
    when(cloudFoundryClient.getOrganizations()).thenReturn(organizations);
    when(organizations.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));

    OnDemandAgent.OnDemandResult result =
        cloudFoundryLoadBalancerCachingAgent.handle(mockProviderCache, data);
    assertThat(result).isNull();
  }

  @Test
  void handleShouldReturnNullWhenLoadBalancerDoesNotExist() {
    String region = "org > space";
    String serverGroupName = "server-group";
    Map<String, Object> data =
        HashMap.<String, Object>of(
                "account", accountName,
                "region", region,
                "serverGroupName", serverGroupName)
            .toJavaMap();

    Organizations organizations = mock(Organizations.class);
    when(cloudFoundryClient.getOrganizations()).thenReturn(organizations);
    when(organizations.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));

    Routes mockRoutes = mock(Routes.class);
    when(cloudFoundryClient.getRoutes()).thenReturn(mockRoutes);
    when(mockRoutes.find(any(), any())).thenReturn(null);

    OnDemandAgent.OnDemandResult result =
        cloudFoundryLoadBalancerCachingAgent.handle(mockProviderCache, data);

    assertThat(result).isNull();
  }

  @Test
  void handleShouldReturnOnDemandResultsWithCacheTimeAndNoProcessedTime() {
    String region = "org > space";
    String loadBalancerName = "server-group";
    Map<String, Object> data =
        HashMap.<String, Object>of(
                "account", accountName,
                "region", region,
                "loadBalancerName", loadBalancerName)
            .toJavaMap();
    CloudFoundryLoadBalancer cloudFoundryLoadBalancer =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-1")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .mappedApps(emptySet())
            .build();
    ResourceCacheData onDemandCacheData =
        new ResourceCacheData(
            Keys.getLoadBalancerKey(accountName, cloudFoundryLoadBalancer),
            cacheView(cloudFoundryLoadBalancer),
            HashMap.<String, Collection<String>>of(SERVER_GROUPS.getNs(), emptyList()).toJavaMap());
    Organizations mockOrganizations = mock(Organizations.class);
    when(mockOrganizations.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));
    Routes mockRoutes = mock(Routes.class);
    when(mockRoutes.find(any(), any())).thenReturn(cloudFoundryLoadBalancer);
    when(mockRoutes.toRouteId(any())).thenReturn(mock(RouteId.class));
    when(cloudFoundryClient.getOrganizations()).thenReturn(mockOrganizations);
    when(cloudFoundryClient.getRoutes()).thenReturn(mockRoutes);
    Map<String, Collection<CacheData>> cacheResults =
        HashMap.<String, Collection<CacheData>>of(
                LOAD_BALANCERS.getNs(), singleton(onDemandCacheData))
            .toJavaMap();
    String sourceAgentType = "account/CloudFoundryLoadBalancerCachingAgent-OnDemand";
    CacheResult expectedCacheResult = new DefaultCacheResult(cacheResults);
    OnDemandAgent.OnDemandResult expectedResult =
        new OnDemandAgent.OnDemandResult(sourceAgentType, expectedCacheResult, emptyMap());

    OnDemandAgent.OnDemandResult result =
        cloudFoundryLoadBalancerCachingAgent.handle(mockProviderCache, data);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }

  @Test
  void pendingOnDemandRequestsShouldReturnOnDemandCacheData() {
    CloudFoundryLoadBalancer cloudFoundryLoadBalancer =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-1")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .mappedApps(emptySet())
            .build();
    Date cacheTime = new Date();
    Long cacheExpiry = 111L;
    Integer processedCount = 1;
    Long processedTime = 222L;
    String loadBalancerKey = Keys.getLoadBalancerKey(accountName, cloudFoundryLoadBalancer);
    Collection<String> expectedKeys = singleton("key1");
    when(mockProviderCache.filterIdentifiers(any(), any())).thenReturn(expectedKeys);
    Collection<CacheData> onDemandCacheData =
        singleton(
            new DefaultCacheData(
                loadBalancerKey,
                HashMap.<String, Object>of(
                        "cacheTime", cacheTime,
                        "cacheExpiry", cacheExpiry,
                        "processedCount", processedCount,
                        "processedTime", processedTime)
                    .toJavaMap(),
                emptyMap()));
    when(mockProviderCache.getAll(any(), any(), any())).thenReturn(onDemandCacheData);
    Moniker moniker = Moniker.builder().build();
    Collection<Map> expectedResult =
        singleton(
            HashMap.of(
                    "id", loadBalancerKey,
                    "details", Keys.parse(loadBalancerKey).get(),
                    "moniker", moniker,
                    "cacheTime", cacheTime,
                    "cacheExpiry", cacheExpiry,
                    "processedCount", processedCount,
                    "processedTime", processedTime)
                .toJavaMap());

    Collection<Map<String, Object>> result =
        cloudFoundryLoadBalancerCachingAgent.pendingOnDemandRequests(mockProviderCache);

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void loadDataShouldReturnCacheResultWithUpdatedData() {
    CloudFoundryLoadBalancer loadBalancer1 =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-1")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .mappedApps(emptySet())
            .build();
    CloudFoundryLoadBalancer loadBalancer2 =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-2")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .mappedApps(emptySet())
            .build();

    when(mockProviderCache.getAll(any(), anyCollection())).thenReturn(emptySet());

    Routes mockRoutes = mock(Routes.class);
    when(mockRoutes.all()).thenReturn(List.of(loadBalancer1, loadBalancer2).toJavaList());

    when(cloudFoundryClient.getRoutes()).thenReturn(mockRoutes);

    CacheData loadBalancerCacheData1 =
        new ResourceCacheData(
            Keys.getLoadBalancerKey(accountName, loadBalancer1),
            cacheView(loadBalancer1),
            Collections.singletonMap(SERVER_GROUPS.getNs(), emptyList()));
    CacheData loadBalancerCacheData2 =
        new ResourceCacheData(
            Keys.getLoadBalancerKey(accountName, loadBalancer2),
            cacheView(loadBalancer2),
            Collections.singletonMap(SERVER_GROUPS.getNs(), emptyList()));

    Map<String, Collection<CacheData>> cacheResults =
        HashMap.<String, Collection<CacheData>>of(
                LOAD_BALANCERS.getNs(),
                HashSet.of(loadBalancerCacheData1, loadBalancerCacheData2).toJavaSet(),
                ON_DEMAND.getNs(),
                emptySet(),
                SERVER_GROUPS.getNs(),
                emptySet())
            .toJavaMap();
    CacheResult expectedCacheResult =
        new DefaultCacheResult(
            cacheResults,
            HashMap.<String, Collection<String>>of(ON_DEMAND.getNs(), emptySet()).toJavaMap());

    CacheResult result = cloudFoundryLoadBalancerCachingAgent.loadData(mockProviderCache);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedCacheResult);
  }

  @Test
  void loadDataShouldReturnCacheResultWithUpdatedDataAndServerGroups() {

    CloudFoundryInstance instance1 = CloudFoundryInstance.builder().appGuid("ap-guid-1").build();

    CloudFoundryServerGroup serverGroup1 =
        CloudFoundryServerGroup.builder()
            .account(accountName)
            .id("sg-guid-1")
            .name("demo")
            .space(cloudFoundrySpace)
            .instances(HashSet.of(instance1).toJavaSet())
            .build();

    CloudFoundryLoadBalancer loadBalancer1 =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-1")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .mappedApps(HashSet.of(serverGroup1).toJavaSet())
            .build();

    when(mockProviderCache.getAll(any(), anyCollection())).thenReturn(emptySet());

    Routes mockRoutes = mock(Routes.class);

    when(mockRoutes.all()).thenReturn(List.of(loadBalancer1).toJavaList());

    when(cloudFoundryClient.getRoutes()).thenReturn(mockRoutes);

    CacheData serverGroupCacheData1 =
        new ResourceCacheData(
            Keys.getServerGroupKey(
                serverGroup1.getAccount(), serverGroup1.getName(), cloudFoundrySpace.getRegion()),
            emptyMap(),
            Collections.singletonMap(
                LOAD_BALANCERS.getNs(), HashSet.of(loadBalancer1.getId()).toJavaList()));

    Map<String, CacheData> loadBalancersByServerGroupIds =
        HashMap.of("1", serverGroupCacheData1).toJavaMap();

    CacheData loadBalancerCacheData1 =
        new ResourceCacheData(
            Keys.getLoadBalancerKey(accountName, loadBalancer1),
            cacheView(loadBalancer1),
            Collections.singletonMap(
                SERVER_GROUPS.getNs(),
                HashSet.of(
                        Keys.getServerGroupKey(
                            serverGroup1.getAccount(),
                            serverGroup1.getName(),
                            cloudFoundrySpace.getRegion()))
                    .toJavaSet()));

    Map<String, Collection<CacheData>> cacheResults =
        HashMap.<String, Collection<CacheData>>of(
                LOAD_BALANCERS.getNs(),
                HashSet.of(loadBalancerCacheData1).toJavaSet(),
                ON_DEMAND.getNs(),
                emptySet(),
                SERVER_GROUPS.getNs(),
                loadBalancersByServerGroupIds.values())
            .toJavaMap();

    CacheResult expectedCacheResult =
        new DefaultCacheResult(
            cacheResults,
            HashMap.<String, Collection<String>>of(ON_DEMAND.getNs(), emptySet()).toJavaMap());

    CacheResult result = cloudFoundryLoadBalancerCachingAgent.loadData(mockProviderCache);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedCacheResult);
  }

  @Test
  void loadDataShouldReturnCacheResultWithDataFromOnDemandNamespace()
      throws JsonProcessingException {

    CloudFoundryLoadBalancer loadBalancer =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-1")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .mappedApps(emptySet())
            .build();

    CloudFoundryServerGroup onDemandCloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .name("serverGroupName")
            .id("sg-guid-1")
            .account(accountName)
            .space(cloudFoundrySpace)
            .diskQuota(1024)
            .build();

    CloudFoundryLoadBalancer onDemandLoadBalancer =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-1")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .mappedApps(singleton(onDemandCloudFoundryServerGroup))
            .build();

    Routes mockRoutes = mock(Routes.class);
    when(mockRoutes.all()).thenReturn(List.of(loadBalancer).toJavaList());

    CacheData onDemandCacheResults =
        new ResourceCacheData(
            Keys.getLoadBalancerKey(accountName, onDemandLoadBalancer),
            cacheView(onDemandLoadBalancer),
            Collections.singletonMap(
                SERVER_GROUPS.getNs(),
                singleton(
                    Keys.getServerGroupKey(
                        accountName,
                        onDemandCloudFoundryServerGroup.getName(),
                        onDemandCloudFoundryServerGroup.getRegion()))));

    when(mockProviderCache.getAll(any(), anyCollection()))
        .thenReturn(
            singleton(
                new DefaultCacheData(
                    Keys.getLoadBalancerKey(accountName, onDemandLoadBalancer),
                    (int) TimeUnit.MINUTES.toSeconds(10), // ttl
                    HashMap.<String, Object>of(
                            "cacheTime",
                            internalClock.instant().plusSeconds(600).toEpochMilli(),
                            "cacheResults",
                            objectMapper.writeValueAsString(
                                Collections.singletonMap(
                                    LOAD_BALANCERS.getNs(),
                                    Collections.singleton(onDemandCacheResults))),
                            "processedCount",
                            0)
                        .toJavaMap(),
                    emptyMap(),
                    internalClock)));

    when(cloudFoundryClient.getRoutes()).thenReturn(mockRoutes);

    Map<String, Collection<CacheData>> cacheResults =
        HashMap.<String, Collection<CacheData>>of(
                LOAD_BALANCERS.getNs(),
                HashSet.of(onDemandCacheResults).toJavaSet(),
                ON_DEMAND.getNs(),
                emptySet(),
                SERVER_GROUPS.getNs(),
                emptySet())
            .toJavaMap();

    CacheResult expectedCacheResult =
        new DefaultCacheResult(
            cacheResults,
            HashMap.<String, Collection<String>>of(ON_DEMAND.getNs(), emptySet()).toJavaMap());

    CacheResult result = cloudFoundryLoadBalancerCachingAgent.loadData(mockProviderCache);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedCacheResult);
  }
}
