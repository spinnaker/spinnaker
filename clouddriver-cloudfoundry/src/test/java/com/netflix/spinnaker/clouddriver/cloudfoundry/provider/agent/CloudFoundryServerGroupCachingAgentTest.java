package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.CloudFoundryServerGroupCachingAgent.cacheView;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.ResourceCacheData;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Applications;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Organizations;
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

class CloudFoundryServerGroupCachingAgentTest {
  private Instant now = Instant.now();
  private String accountName = "account";
  private ObjectMapper objectMapper =
      new ObjectMapper().disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
  private CloudFoundryClient cloudFoundryClient = mock(CloudFoundryClient.class);
  private CloudFoundryCredentials credentials = mock(CloudFoundryCredentials.class);
  private Registry registry = mock(Registry.class);
  private final Clock internalClock = Clock.fixed(now, ZoneId.systemDefault());
  private CloudFoundryServerGroupCachingAgent cloudFoundryServerGroupCachingAgent =
      new CloudFoundryServerGroupCachingAgent(credentials, registry);
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
  void buildOnDemandCacheDataShouldIncludeServerGroupAttributes() throws JsonProcessingException {

    CloudFoundryInstance cloudFoundryInstance =
        CloudFoundryInstance.builder().appGuid("instance-guid-1").key("instance-key").build();

    CloudFoundryServerGroup onDemandCloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .name("serverGroupName")
            .id("sg-guid-1")
            .account(accountName)
            .space(cloudFoundrySpace)
            .diskQuota(1024)
            .instances(singleton(cloudFoundryInstance))
            .build();

    Map<String, Collection<String>> serverGroupRelationships =
        HashMap.<String, Collection<String>>of(
                INSTANCES.getNs(),
                singleton(Keys.getInstanceKey(accountName, cloudFoundryInstance.getName())),
                LOAD_BALANCERS.getNs(),
                emptyList())
            .toJavaMap();

    ResourceCacheData onDemandCacheResults =
        new ResourceCacheData(
            Keys.getServerGroupKey(
                accountName,
                onDemandCloudFoundryServerGroup.getName(),
                onDemandCloudFoundryServerGroup.getRegion()),
            cacheView(onDemandCloudFoundryServerGroup),
            serverGroupRelationships);

    CacheData cacheData =
        cloudFoundryServerGroupCachingAgent.buildOnDemandCacheData(
            Keys.getServerGroupKey(
                accountName,
                onDemandCloudFoundryServerGroup.getName(),
                onDemandCloudFoundryServerGroup.getRegion()),
            Collections.singletonMap(
                SERVER_GROUPS.getNs(), Collections.singleton(onDemandCacheResults)));

    ResourceCacheData result =
        objectMapper
            .readValue(
                cacheData.getAttributes().get("cacheResults").toString(),
                new TypeReference<Map<String, Collection<ResourceCacheData>>>() {})
            .get("serverGroups")
            .stream()
            .findFirst()
            .get();

    assertThat(result).isEqualToComparingFieldByFieldRecursively(onDemandCacheResults);
  }

  @Test
  void handleShouldReturnNullWhenAccountDoesNotMatch() {
    Map<String, Object> data = HashMap.<String, Object>of("account", "other-account").toJavaMap();

    OnDemandAgent.OnDemandResult result =
        cloudFoundryServerGroupCachingAgent.handle(mockProviderCache, data);
    assertThat(result).isNull();
  }

  @Test
  void handleShouldReturnNullWhenRegionIsUnspecified() {
    OnDemandAgent.OnDemandResult result =
        cloudFoundryServerGroupCachingAgent.handle(mockProviderCache, emptyMap());

    assertThat(result).isNull();
  }

  @Test
  void handleShouldReturnNullWhenRegionDoesNotExist() {
    String region = "org > space";
    Map<String, Object> data =
        HashMap.<String, Object>of(
                "account", accountName,
                "region", region)
            .toJavaMap();

    Organizations organizations = mock(Organizations.class);
    when(organizations.findSpaceByRegion(any())).thenReturn(Optional.empty());

    when(cloudFoundryClient.getOrganizations()).thenReturn(organizations);

    OnDemandAgent.OnDemandResult result =
        cloudFoundryServerGroupCachingAgent.handle(mockProviderCache, data);
    assertThat(result).isNull();
    verify(organizations).findSpaceByRegion(eq(region));
  }

  @Test
  void handleShouldReturnNullWhenServerGroupNameIsUnspecified() {
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
        cloudFoundryServerGroupCachingAgent.handle(mockProviderCache, data);
    assertThat(result).isNull();
    verify(organizations).findSpaceByRegion(eq(region));
  }

  @Test
  void handleShouldReturnAnEvictResultWhenServerGroupDoesNotExists() {
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

    Applications mockApplications = mock(Applications.class);
    when(cloudFoundryClient.getApplications()).thenReturn(mockApplications);
    when(mockApplications.findServerGroupByNameAndSpaceId(any(), any())).thenReturn(null);

    when(mockProviderCache.filterIdentifiers(any(), any()))
        .thenReturn(Collections.singletonList("key"));

    OnDemandAgent.OnDemandResult result =
        cloudFoundryServerGroupCachingAgent.handle(mockProviderCache, data);

    assertThat(result).isNotNull();
    assertThat(result.getEvictions()).hasSize(1);
    assertThat(result.getEvictions().get(SERVER_GROUPS.getNs())).containsExactly("key");
  }

  @Test
  void handleShouldReturnOnDemandResultsWithCacheTimeAndNoProcessedTime() {
    String region = "org > space";
    String serverGroupName = "server-group";
    Map<String, Object> data =
        HashMap.<String, Object>of(
                "account", accountName,
                "region", region,
                "serverGroupName", serverGroupName)
            .toJavaMap();
    CloudFoundryInstance cloudFoundryInstance =
        CloudFoundryInstance.builder().appGuid("instance-guid").key("instance-key").build();
    CloudFoundryServerGroup matchingCloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .name(serverGroupName)
            .account(accountName)
            .space(cloudFoundrySpace)
            .instances(singleton(cloudFoundryInstance))
            .build();
    ResourceCacheData onDemandCacheData =
        new ResourceCacheData(
            Keys.getServerGroupKey(accountName, serverGroupName, region),
            cacheView(matchingCloudFoundryServerGroup),
            HashMap.<String, Collection<String>>of(
                    INSTANCES.getNs(),
                        Collections.singletonList(
                            Keys.getInstanceKey(accountName, cloudFoundryInstance.getName())),
                    LOAD_BALANCERS.getNs(), Collections.emptyList())
                .toJavaMap());
    Organizations mockOrganizations = mock(Organizations.class);
    when(mockOrganizations.findSpaceByRegion(any())).thenReturn(Optional.of(cloudFoundrySpace));
    Applications mockApplications = mock(Applications.class);
    when(mockApplications.findServerGroupByNameAndSpaceId(any(), any()))
        .thenReturn(matchingCloudFoundryServerGroup);
    when(cloudFoundryClient.getOrganizations()).thenReturn(mockOrganizations);
    when(cloudFoundryClient.getApplications()).thenReturn(mockApplications);
    Map<String, Collection<CacheData>> cacheResults =
        HashMap.<String, Collection<CacheData>>of(
                SERVER_GROUPS.getNs(), singleton(onDemandCacheData))
            .toJavaMap();
    String sourceAgentType = "account/CloudFoundryServerGroupCachingAgent-OnDemand";
    CacheResult expectedCacheResult = new DefaultCacheResult(cacheResults);
    OnDemandAgent.OnDemandResult expectedResult =
        new OnDemandAgent.OnDemandResult(sourceAgentType, expectedCacheResult, emptyMap());

    OnDemandAgent.OnDemandResult result =
        cloudFoundryServerGroupCachingAgent.handle(mockProviderCache, data);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }

  @Test
  void pendingOnDemandRequestsShouldReturnOnDemandCacheData() {
    String serverGroupName = "application-stack-detail-v000";
    String region = "org > space";
    Date cacheTime = new Date();
    Long cacheExpiry = 111L;
    Integer processedCount = 1;
    Long processedTime = 222L;
    String serverGroupKey = Keys.getServerGroupKey(accountName, serverGroupName, region);
    Collection<String> expectedKeys = singleton("key1");
    when(mockProviderCache.filterIdentifiers(any(), any())).thenReturn(expectedKeys);
    Collection<CacheData> onDemandCacheData =
        singleton(
            new DefaultCacheData(
                serverGroupKey,
                HashMap.<String, Object>of(
                        "cacheTime", cacheTime,
                        "cacheExpiry", cacheExpiry,
                        "processedCount", processedCount,
                        "processedTime", processedTime)
                    .toJavaMap(),
                emptyMap()));
    when(mockProviderCache.getAll(any(), any(), any())).thenReturn(onDemandCacheData);
    Moniker moniker =
        Moniker.builder()
            .app("application")
            .stack("stack")
            .detail("detail")
            .cluster("application-stack-detail")
            .sequence(0)
            .build();
    Collection<Map> expectedResult =
        singleton(
            HashMap.of(
                    "id", serverGroupKey,
                    "details", Keys.parse(serverGroupKey).get(),
                    "moniker", moniker,
                    "cacheTime", cacheTime,
                    "cacheExpiry", cacheExpiry,
                    "processedCount", processedCount,
                    "processedTime", processedTime)
                .toJavaMap());

    Collection<Map<String, Object>> result =
        cloudFoundryServerGroupCachingAgent.pendingOnDemandRequests(mockProviderCache);

    assertThat(result).isEqualTo(expectedResult);
    verify(mockProviderCache)
        .filterIdentifiers(
            eq(ON_DEMAND.getNs()), eq(Keys.getServerGroupKey(accountName, "*", "*")));
    verify(mockProviderCache)
        .getAll(eq(ON_DEMAND.getNs()), eq(expectedKeys), refEq(RelationshipCacheFilter.none()));
  }

  @Test
  void convertOnDemandDetailsShouldReturnNullMonikerForNullMonikerData() {
    Moniker result = cloudFoundryServerGroupCachingAgent.convertOnDemandDetails(null);

    assertThat(result).isNull();
  }

  @Test
  void convertOnDemandDetailsShouldReturnNullMonikerForNoServerGroupInMonikerData() {
    Moniker result = cloudFoundryServerGroupCachingAgent.convertOnDemandDetails(emptyMap());

    assertThat(result).isNull();
  }

  @Test
  void convertOnDemandDetailsShouldReturnMonikerDataForServerGroup() {
    Moniker expectedMoniker =
        Moniker.builder()
            .app("app")
            .stack("stack")
            .detail("detail")
            .cluster("app-stack-detail")
            .sequence(235)
            .build();

    Moniker result =
        cloudFoundryServerGroupCachingAgent.convertOnDemandDetails(
            singletonMap("serverGroupName", "app-stack-detail-v235"));

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedMoniker);
  }

  @Test
  void loadDataShouldReturnCacheResultWithUpdatedData() {
    String region = "org > space";
    String appName1 = "app1";
    String appName3 = "app3";
    String clusterName1 = appName1 + "-stack1-detail1";
    String clusterName3 = appName3 + "-stack3-detail3";
    String serverGroupName1 = clusterName1 + "-v000";
    String serverGroupName2 = clusterName1 + "-v001";
    String serverGroupName3 = clusterName3 + "-v000";
    String instanceId1 = "instance-guid-1-instance-key";
    String serverGroupKey1 = Keys.getServerGroupKey(accountName, serverGroupName1, region);
    String serverGroupKey2 = Keys.getServerGroupKey(accountName, serverGroupName2, region);
    String serverGroupKey3 = Keys.getServerGroupKey(accountName, serverGroupName3, region);

    CloudFoundryLoadBalancer loadBalancer1 =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-1")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .build();
    CloudFoundryLoadBalancer loadBalancer2 =
        CloudFoundryLoadBalancer.builder()
            .account(accountName)
            .id("lb-guid-2")
            .domain(CloudFoundryDomain.builder().name("domain-name").build())
            .build();
    CloudFoundryInstance instance1 =
        CloudFoundryInstance.builder().appGuid("instance-guid-1").key("instance-key").build();
    CloudFoundryServerGroup serverGroup1 =
        CloudFoundryServerGroup.builder()
            .name(serverGroupName1)
            .id("sg-guid-1")
            .account(accountName)
            .space(cloudFoundrySpace)
            .instances(singleton(instance1))
            .loadBalancerNames(Collections.singleton(loadBalancer1.getName()))
            .build();
    CloudFoundryServerGroup serverGroup2 =
        CloudFoundryServerGroup.builder()
            .name(serverGroupName2)
            .id("sg-guid-2")
            .account(accountName)
            .space(cloudFoundrySpace)
            .instances(emptySet())
            .loadBalancerNames(Collections.singleton(loadBalancer2.getName()))
            .build();
    CloudFoundryServerGroup serverGroup3 =
        CloudFoundryServerGroup.builder()
            .name(serverGroupName3)
            .id("sg-guid-3")
            .account(accountName)
            .space(cloudFoundrySpace)
            .instances(emptySet())
            .build();

    CloudFoundryCluster cloudFoundryCluster1 =
        CloudFoundryCluster.builder()
            .accountName(accountName)
            .name(clusterName1)
            .serverGroups(HashSet.of(serverGroup1, serverGroup2).toJavaSet())
            .build();
    CloudFoundryCluster cloudFoundryCluster3 =
        CloudFoundryCluster.builder()
            .accountName(accountName)
            .name(clusterName3)
            .serverGroups(singleton(serverGroup3))
            .build();
    CloudFoundryApplication cloudFoundryApplication1 =
        CloudFoundryApplication.builder()
            .name(appName1)
            .clusters(singleton(cloudFoundryCluster1))
            .build();
    CloudFoundryApplication cloudFoundryApplication3 =
        CloudFoundryApplication.builder()
            .name(appName3)
            .clusters(singleton(cloudFoundryCluster3))
            .build();

    when(mockProviderCache.getAll(any(), anyCollection())).thenReturn(emptySet());

    Applications mockApplications = mock(Applications.class);
    when(mockApplications.all())
        .thenReturn(List.of(cloudFoundryApplication1, cloudFoundryApplication3).toJavaList());

    when(cloudFoundryClient.getApplications()).thenReturn(mockApplications);

    Map<String, Collection<String>> applicationRelationships1 =
        HashMap.<String, Collection<String>>of(
                CLUSTERS.getNs(),
                singleton(Keys.getClusterKey(accountName, appName1, clusterName1)))
            .toJavaMap();
    Map<String, Collection<String>> applicationRelationships3 =
        HashMap.<String, Collection<String>>of(
                CLUSTERS.getNs(),
                singleton(Keys.getClusterKey(accountName, appName3, clusterName3)))
            .toJavaMap();
    CacheData applicationsCacheData1 =
        new ResourceCacheData(
            Keys.getApplicationKey(appName1),
            cacheView(cloudFoundryApplication1),
            applicationRelationships1);
    CacheData applicationsCacheData3 =
        new ResourceCacheData(
            Keys.getApplicationKey(appName3),
            cacheView(cloudFoundryApplication3),
            applicationRelationships3);

    Map<String, Collection<String>> serverGroupRelationships1 =
        HashMap.<String, Collection<String>>of(
                INSTANCES.getNs(), singleton(Keys.getInstanceKey(accountName, instanceId1)),
                LOAD_BALANCERS.getNs(),
                    singleton(
                        Keys.getLoadBalancerKey(
                            accountName, loadBalancer1.getName(), serverGroup1.getRegion())))
            .toJavaMap();
    Map<String, Collection<String>> serverGroupRelationships2 =
        HashMap.<String, Collection<String>>of(
                INSTANCES.getNs(), emptySet(),
                LOAD_BALANCERS.getNs(),
                    singleton(
                        Keys.getLoadBalancerKey(
                            accountName, loadBalancer2.getName(), serverGroup2.getRegion())))
            .toJavaMap();
    Map<String, Collection<String>> serverGroupRelationships3 =
        HashMap.<String, Collection<String>>of(
                INSTANCES.getNs(), emptySet(),
                LOAD_BALANCERS.getNs(), emptySet())
            .toJavaMap();

    CacheData serverGroupCacheData1 =
        new ResourceCacheData(serverGroupKey1, cacheView(serverGroup1), serverGroupRelationships1);
    CacheData serverGroupCacheData2 =
        new ResourceCacheData(serverGroupKey2, cacheView(serverGroup2), serverGroupRelationships2);
    CacheData serverGroupCacheData3 =
        new ResourceCacheData(serverGroupKey3, cacheView(serverGroup3), serverGroupRelationships3);

    Map<String, Collection<String>> clusterRelationships1 =
        HashMap.<String, Collection<String>>of(
                SERVER_GROUPS.getNs(), HashSet.of(serverGroupKey1, serverGroupKey2).toJavaSet())
            .toJavaMap();
    Map<String, Collection<String>> clusterRelationships3 =
        HashMap.<String, Collection<String>>of(SERVER_GROUPS.getNs(), singleton(serverGroupKey3))
            .toJavaMap();

    String clusterKey1 = Keys.getClusterKey(accountName, appName1, clusterName1);
    String clusterKey3 = Keys.getClusterKey(accountName, appName3, clusterName3);
    CacheData clusterCacheData1 =
        new ResourceCacheData(clusterKey1, cacheView(cloudFoundryCluster1), clusterRelationships1);
    CacheData clusterCacheData3 =
        new ResourceCacheData(clusterKey3, cacheView(cloudFoundryCluster3), clusterRelationships3);

    CacheData instanceCacheData =
        new ResourceCacheData(
            Keys.getInstanceKey(accountName, instanceId1), cacheView(instance1), emptyMap());

    Map<String, Collection<CacheData>> cacheResults =
        HashMap.<String, Collection<CacheData>>of(
                APPLICATIONS.getNs(),
                    HashSet.of(applicationsCacheData1, applicationsCacheData3).toJavaSet(),
                CLUSTERS.getNs(), HashSet.of(clusterCacheData1, clusterCacheData3).toJavaSet(),
                SERVER_GROUPS.getNs(),
                    HashSet.of(serverGroupCacheData1, serverGroupCacheData2, serverGroupCacheData3)
                        .toJavaSet(),
                INSTANCES.getNs(), singleton(instanceCacheData),
                ON_DEMAND.getNs(), emptySet())
            .toJavaMap();
    CacheResult expectedCacheResult =
        new DefaultCacheResult(
            cacheResults,
            HashMap.<String, Collection<String>>of(ON_DEMAND.getNs(), emptySet()).toJavaMap());

    CacheResult result = cloudFoundryServerGroupCachingAgent.loadData(mockProviderCache);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedCacheResult);
    verify(mockApplications).all();
  }

  @Test
  void loadDataShouldReturnCacheResultWithDataFromOnDemandNamespace()
      throws JsonProcessingException {

    CloudFoundryInstance cloudFoundryInstance =
        CloudFoundryInstance.builder().appGuid("instance-guid-1").key("instance-key").build();

    CloudFoundryServerGroup cloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .name("serverGroupName")
            .id("sg-guid-1")
            .account(accountName)
            .space(cloudFoundrySpace)
            .instances(singleton(cloudFoundryInstance))
            .build();

    CloudFoundryServerGroup onDemandCloudFoundryServerGroup =
        CloudFoundryServerGroup.builder()
            .name("serverGroupName")
            .id("sg-guid-1")
            .account(accountName)
            .space(cloudFoundrySpace)
            .diskQuota(1024)
            .instances(singleton(cloudFoundryInstance))
            .build();

    CloudFoundryCluster cloudFoundryCluster =
        CloudFoundryCluster.builder()
            .accountName(accountName)
            .name("clusterName-foo-bar")
            .serverGroups(Collections.singleton(cloudFoundryServerGroup))
            .build();

    CloudFoundryApplication cloudFoundryApplication =
        CloudFoundryApplication.builder()
            .name("appName")
            .clusters(singleton(cloudFoundryCluster))
            .build();

    Map<String, Collection<String>> applicationRelationships =
        HashMap.<String, Collection<String>>of(
                CLUSTERS.getNs(),
                singleton(
                    Keys.getClusterKey(
                        accountName,
                        cloudFoundryApplication.getName(),
                        cloudFoundryCluster.getName())))
            .toJavaMap();

    Map<String, Collection<String>> clusterRelationships =
        HashMap.<String, Collection<String>>of(
                SERVER_GROUPS.getNs(),
                HashSet.of(
                        Keys.getServerGroupKey(
                            accountName,
                            cloudFoundryServerGroup.getName(),
                            cloudFoundryServerGroup.getRegion()))
                    .toJavaSet())
            .toJavaMap();

    Map<String, Collection<String>> serverGroupRelationships =
        HashMap.<String, Collection<String>>of(
                INSTANCES.getNs(),
                    singleton(Keys.getInstanceKey(accountName, cloudFoundryInstance.getName())),
                LOAD_BALANCERS.getNs(), emptyList())
            .toJavaMap();

    Applications mockApplications = mock(Applications.class);
    when(mockApplications.all()).thenReturn(List.of(cloudFoundryApplication).toJavaList());

    ResourceCacheData onDemandCacheResults =
        new ResourceCacheData(
            Keys.getServerGroupKey(
                accountName,
                onDemandCloudFoundryServerGroup.getName(),
                onDemandCloudFoundryServerGroup.getRegion()),
            cacheView(onDemandCloudFoundryServerGroup),
            serverGroupRelationships);

    when(mockProviderCache.getAll(any(), anyCollection()))
        .thenReturn(
            singleton(
                new DefaultCacheData(
                    Keys.getServerGroupKey(
                        accountName,
                        onDemandCloudFoundryServerGroup.getName(),
                        onDemandCloudFoundryServerGroup.getRegion()),
                    (int) TimeUnit.MINUTES.toSeconds(10), // ttl
                    io.vavr.collection.HashMap.<String, Object>of(
                            "cacheTime",
                            internalClock.instant().plusSeconds(600).toEpochMilli(),
                            "cacheResults",
                            objectMapper.writeValueAsString(
                                Collections.singletonMap(
                                    SERVER_GROUPS.getNs(),
                                    Collections.singleton(onDemandCacheResults))),
                            "processedCount",
                            0)
                        .toJavaMap(),
                    emptyMap(),
                    internalClock)));
    when(cloudFoundryClient.getApplications()).thenReturn(mockApplications);

    Map<String, Collection<CacheData>> cacheResults =
        HashMap.<String, Collection<CacheData>>of(
                APPLICATIONS.getNs(),
                Collections.singleton(
                    new ResourceCacheData(
                        Keys.getApplicationKey(cloudFoundryApplication.getName()),
                        cacheView(cloudFoundryApplication),
                        applicationRelationships)),
                CLUSTERS.getNs(),
                Collections.singleton(
                    new ResourceCacheData(
                        Keys.getClusterKey(
                            accountName,
                            cloudFoundryCluster.getMoniker().getApp(),
                            cloudFoundryCluster.getName()),
                        cacheView(cloudFoundryCluster),
                        clusterRelationships)),
                SERVER_GROUPS.getNs(),
                Collections.singleton(
                    new ResourceCacheData(
                        Keys.getServerGroupKey(
                            accountName,
                            onDemandCloudFoundryServerGroup.getName(),
                            onDemandCloudFoundryServerGroup.getRegion()),
                        cacheView(onDemandCloudFoundryServerGroup),
                        serverGroupRelationships)),
                INSTANCES.getNs(),
                singleton(
                    new ResourceCacheData(
                        Keys.getInstanceKey(accountName, cloudFoundryInstance.getName()),
                        cacheView(cloudFoundryInstance),
                        emptyMap())),
                ON_DEMAND.getNs(),
                emptySet())
            .toJavaMap();

    CacheResult expectedCacheResult =
        new DefaultCacheResult(
            cacheResults,
            HashMap.<String, Collection<String>>of(ON_DEMAND.getNs(), emptySet()).toJavaMap());

    CacheResult result = cloudFoundryServerGroupCachingAgent.loadData(mockProviderCache);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedCacheResult);
  }
}
