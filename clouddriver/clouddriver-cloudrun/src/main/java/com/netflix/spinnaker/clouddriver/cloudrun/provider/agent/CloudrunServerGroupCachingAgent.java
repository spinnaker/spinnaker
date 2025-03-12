package com.netflix.spinnaker.clouddriver.cloudrun.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys.Namespace.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.run.v1.CloudRun;
import com.google.api.services.run.v1.model.ListRevisionsResponse;
import com.google.api.services.run.v1.model.Revision;
import com.google.api.services.run.v1.model.Service;
import com.google.common.collect.ImmutableSet;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunInstance;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunServerGroup;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import groovy.lang.Reference;
import groovy.util.logging.Slf4j;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
@Slf4j
public class CloudrunServerGroupCachingAgent extends AbstractCloudrunCachingAgent
    implements OnDemandAgent {

  private final String category = "serverGroup";
  private final String NAME = "name";
  private final String CLOUDRUN_INSTANCE = "-instance";
  private final String PARENT_PREFIX = "namespaces/";
  private final OnDemandMetricsSupport metricsSupport;
  private static final Set<AgentDataType> types =
      ImmutableSet.of(
          AUTHORITATIVE.forType(APPLICATIONS.getNs()),
          AUTHORITATIVE.forType(CLUSTERS.getNs()),
          AUTHORITATIVE.forType(SERVER_GROUPS.getNs()),
          AUTHORITATIVE.forType(INSTANCES.getNs()),
          INFORMATIVE.forType(LOAD_BALANCERS.getNs()));
  private String agentType = getAccountName() + "/" + getSimpleName();

  public CloudrunServerGroupCachingAgent(
      String accountName,
      CloudrunNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry) {
    super(accountName, objectMapper, credentials);
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, CloudrunCloudProvider.ID + ":" + OnDemandType.ServerGroup);
  }

  @Override
  public String getSimpleName() {
    return CloudrunServerGroupCachingAgent.class.getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.ServerGroup) && cloudProvider.equals(CloudrunCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {

    if (!data.containsKey("serverGroupName") || data.get("account") != getAccountName()) {
      return null;
    }
    String serverGroupName = data.get("serverGroupName").toString();
    Map<String, Object> matchingServerGroupAndLoadBalancer =
        metricsSupport.readData(() -> loadServerGroupAndLoadBalancer(serverGroupName));
    if (matchingServerGroupAndLoadBalancer.isEmpty()) {
      return null;
    }
    Revision serverGroup = (Revision) matchingServerGroupAndLoadBalancer.get("serverGroup");
    Service loadBalancer = (Service) matchingServerGroupAndLoadBalancer.get("loadBalancer");
    Map<Service, List<Revision>> serverGroupsByLoadBalancer =
        Map.of(loadBalancer, List.of(serverGroup));
    CacheResult result =
        metricsSupport.transformData(
            () ->
                buildCacheResult(
                    serverGroupsByLoadBalancer,
                    new HashMap<>(),
                    new ArrayList<>(),
                    Long.MAX_VALUE));
    String serverGroupKey =
        Keys.getServerGroupKey(
            getAccountName(), serverGroupName, CloudrunServerGroup.getRegion(serverGroup));
    try {
      String jsonResult = getObjectMapper().writeValueAsString(result.getCacheResults());
      if (result.getCacheResults().values().stream().flatMap(Collection::stream).count() == 0) {
        providerCache.evictDeletedItems(ON_DEMAND.getNs(), Set.of(serverGroupKey));
      } else {
        metricsSupport.onDemandStore(
            () -> {
              CacheData cacheData =
                  new DefaultCacheData(
                      serverGroupKey,
                      10 * 60, // ttl is 10 minutes.
                      Map.of(
                          "cacheTime",
                          System.currentTimeMillis(),
                          "cacheResults",
                          jsonResult,
                          "processedCount",
                          0,
                          "processedTime",
                          null),
                      Map.of());
              providerCache.putCacheData(ON_DEMAND.getNs(), cacheData);
              return null;
            });
      }
      Map<String, Collection<String>> evictions = Map.of();
      logger.info("On demand cache refresh (data: {}) succeeded.", data);
      return new OnDemandResult(getOnDemandAgentType(), result, evictions);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("On demand cache refresh failed. Error message : " + e);
    }
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    long start = System.currentTimeMillis();
    Map<Service, List<Revision>> serverGroupsByLoadBalancer = loadServerGroups();
    Map<Revision, String> instancesByServerGroup = loadInstances(serverGroupsByLoadBalancer);
    List<CacheData> evictFromOnDemand = new ArrayList<>();
    List<CacheData> keepInOnDemand = new ArrayList<>();

    Collection<String> serverGroupKeys =
        serverGroupsByLoadBalancer.values().stream()
            .flatMap(Collection::stream)
            .map(
                revision ->
                    Keys.getServerGroupKey(
                        getAccountName(), getRevisionName(revision), getRegion(revision)))
            .collect(Collectors.toSet());
    providerCache
        .getAll(ON_DEMAND.getNs(), serverGroupKeys)
        .forEach(
            onDemandEntry -> {
              String cacheTime = (String) onDemandEntry.getAttributes().get("cacheTime");
              String processedCount = (String) onDemandEntry.getAttributes().get("processedCount");

              if (cacheTime != null
                  && Long.parseLong(cacheTime) < start
                  && processedCount != null
                  && Integer.parseInt(processedCount) > 0) {
                evictFromOnDemand.add(onDemandEntry);
              } else {
                keepInOnDemand.add(onDemandEntry);
              }
            });

    Map<String, CacheData> onDemandMap = new HashMap<>();
    keepInOnDemand.forEach(cacheData -> onDemandMap.put(cacheData.getId(), cacheData));
    List<String> onDemandEvict =
        evictFromOnDemand.stream().map(CacheData::getId).collect(Collectors.toList());
    CacheResult cacheResult =
        buildCacheResult(serverGroupsByLoadBalancer, onDemandMap, onDemandEvict, start);

    cacheResult
        .getCacheResults()
        .get(ON_DEMAND.getNs())
        .forEach(
            onDemandEntry -> {
              onDemandEntry.getAttributes().put("processedTime", System.currentTimeMillis());
              Object processedCountObj = onDemandEntry.getAttributes().get("processedCount");
              int processedCount = 0;
              if (processedCountObj != null) {
                processedCount = (int) processedCountObj;
              }
              onDemandEntry.getAttributes().put("processedCount", processedCount + 1);
            });
    return cacheResult;
  }

  public CacheResult buildCacheResult(
      Map<Service, List<Revision>> serverGroupsByLoadBalancer,
      Map<String, CacheData> onDemandKeep,
      List<String> onDemandEvict,
      Long start) {
    logger.info("Describing items in " + getAgentType());

    Map<String, CacheData> cachedApplications = new HashMap<>();
    Map<String, CacheData> cachedClusters = new HashMap<>();
    Map<String, CacheData> cachedServerGroups = new HashMap<>();
    Map<String, CacheData> cachedLoadBalancers = new HashMap<>();
    Map<String, CacheData> cachedInstances = new HashMap<>();

    serverGroupsByLoadBalancer.forEach(
        (loadBalancer, serverGroups) -> {
          String loadBalancerName = loadBalancer.getMetadata().getName();
          String application =
              loadBalancer.getMetadata().getAnnotations().get("spinnaker/application");
          serverGroups.forEach(
              serverGroup -> {
                String region = getRegion(serverGroup);
                if (!onDemandKeep.isEmpty()) {
                  CacheData onDemandData =
                      onDemandKeep.get(
                          Keys.getServerGroupKey(
                              getAccountName(), serverGroup.getMetadata().getName(), region));

                  if (onDemandData != null
                      && onDemandData.getAttributes().get("cacheTime") != null
                      && Long.parseLong(onDemandData.getAttributes().get("cacheTime").toString())
                          >= start) {
                    Map<String, List<CacheData>> cacheResults;
                    try {
                      cacheResults =
                          getObjectMapper()
                              .readValue(
                                  onDemandData.getAttributes().get("cacheResults").toString(),
                                  new TypeReference<>() {});
                    } catch (JsonProcessingException e) {
                      throw new RuntimeException(e);
                    }
                    cache(cacheResults, APPLICATIONS.getNs(), cachedApplications);
                    cache(cacheResults, CLUSTERS.getNs(), cachedClusters);
                    cache(cacheResults, SERVER_GROUPS.getNs(), cachedServerGroups);
                    cache(cacheResults, INSTANCES.getNs(), cachedInstances);
                    cache(cacheResults, LOAD_BALANCERS.getNs(), cachedLoadBalancers);
                  }
                } else {
                  String serverGroupName = serverGroup.getMetadata().getName();
                  Names names = Names.parseName(serverGroupName);
                  String applicationName = application;
                  if (applicationName == null) {
                    applicationName = names.getApp();
                  }
                  String clusterName = names.getCluster();
                  // no instances
                  String serverGroupKey =
                      Keys.getServerGroupKey(getAccountName(), serverGroupName, region);
                  String applicationKey = Keys.getApplicationKey(applicationName);
                  String clusterKey =
                      Keys.getClusterKey(getAccountName(), applicationName, clusterName);
                  String loadBalancerKey =
                      Keys.getLoadBalancerKey(getAccountName(), loadBalancerName);
                  // application data
                  MutableCacheData applicationData;
                  if (cachedApplications.isEmpty()
                      || !cachedApplications.containsKey(applicationKey)) {
                    applicationData = new MutableCacheData(applicationKey);
                    applicationData.getAttributes().put(NAME, applicationName);
                    Map<String, Collection<String>> applicationRelationships =
                        applicationData.getRelationships();
                    applicationRelationships.put(
                        CLUSTERS.getNs(),
                        new HashSet<>() {
                          {
                            add(clusterKey);
                          }
                        });
                    applicationRelationships.put(
                        SERVER_GROUPS.getNs(),
                        new HashSet<>() {
                          {
                            add(serverGroupKey);
                          }
                        });
                    applicationRelationships.put(
                        LOAD_BALANCERS.getNs(),
                        new HashSet<>() {
                          {
                            add(loadBalancerKey);
                          }
                        });
                    cachedApplications.put(applicationKey, applicationData);
                  } else {
                    applicationData = (MutableCacheData) cachedApplications.get(applicationKey);
                    applicationData.getRelationships().get(CLUSTERS.getNs()).add(clusterKey);
                    applicationData
                        .getRelationships()
                        .get(SERVER_GROUPS.getNs())
                        .add(serverGroupKey);
                    applicationData
                        .getRelationships()
                        .get(LOAD_BALANCERS.getNs())
                        .add(loadBalancerKey);
                  }

                  // cluster data
                  MutableCacheData clusterData;
                  if (cachedClusters.isEmpty() || !cachedClusters.containsKey(clusterKey)) {
                    clusterData = new MutableCacheData(clusterKey);
                    clusterData.getAttributes().put(NAME, clusterName);
                    Map<String, Collection<String>> clusterRelationships =
                        clusterData.getRelationships();
                    clusterRelationships.put(APPLICATIONS.getNs(), Set.of(applicationKey));
                    clusterRelationships.put(
                        SERVER_GROUPS.getNs(),
                        new HashSet<>() {
                          {
                            add(serverGroupKey);
                          }
                        });
                    clusterRelationships.put(LOAD_BALANCERS.getNs(), Set.of(loadBalancerKey));
                    cachedClusters.put(clusterKey, clusterData);
                  } else {
                    clusterData = (MutableCacheData) cachedClusters.get(clusterKey);
                    clusterData.getRelationships().get(SERVER_GROUPS.getNs()).add(serverGroupKey);
                  }

                  // instance data
                  String instanceName = serverGroupName + CLOUDRUN_INSTANCE;
                  String instanceKey = Keys.getInstanceKey(getAccountName(), instanceName);
                  MutableCacheData instanceData = new MutableCacheData(instanceKey);
                  instanceData.getAttributes().put(NAME, instanceName);
                  instanceData
                      .getAttributes()
                      .put("instance", new CloudrunInstance(serverGroup, loadBalancer, region));
                  Map<String, Collection<String>> instanceRelationships =
                      instanceData.getRelationships();
                  instanceRelationships.put(APPLICATIONS.getNs(), Set.of(applicationKey));
                  instanceRelationships.put(CLUSTERS.getNs(), Set.of(clusterKey));
                  instanceRelationships.put(SERVER_GROUPS.getNs(), Set.of(serverGroupKey));
                  instanceRelationships.put(LOAD_BALANCERS.getNs(), Set.of(loadBalancerKey));
                  cachedInstances.put(instanceName, instanceData);
                  // server group data
                  CloudrunServerGroup cloudrunServerGroup =
                      new CloudrunServerGroup(serverGroup, getAccountName(), loadBalancerName);

                  MutableCacheData serverGroupData = new MutableCacheData(serverGroupKey);
                  serverGroupData.getAttributes().put(NAME, serverGroupName);
                  serverGroupData.getAttributes().put("serverGroup", cloudrunServerGroup);
                  Map<String, Collection<String>> serverGroupRelationships =
                      serverGroupData.getRelationships();
                  serverGroupRelationships.put(APPLICATIONS.getNs(), Set.of(applicationKey));
                  serverGroupRelationships.put(CLUSTERS.getNs(), Set.of(clusterKey));
                  serverGroupRelationships.put(INSTANCES.getNs(), Set.of(instanceKey));
                  serverGroupRelationships.put(LOAD_BALANCERS.getNs(), Set.of(loadBalancerKey));
                  cachedServerGroups.put(serverGroupKey, serverGroupData);

                  // loadbalancer data
                  MutableCacheData loadbalancerData;
                  if (cachedLoadBalancers.isEmpty()
                      || !cachedLoadBalancers.containsKey(loadBalancerKey)) {
                    loadbalancerData = new MutableCacheData(loadBalancerKey);
                    loadbalancerData.getAttributes().put(NAME, loadBalancerName);
                    loadbalancerData
                        .getAttributes()
                        .put(
                            "loadBalancer",
                            new CloudrunLoadBalancer(
                                loadBalancer, getAccountName(), getRegion(serverGroup)));
                    Set<String> serverGroupKeySet = new HashSet<>();
                    serverGroupKeySet.add(serverGroupKey);
                    Set<String> instanceKeySet = new HashSet<>();
                    instanceKeySet.add(instanceKey);
                    loadbalancerData
                        .getRelationships()
                        .put(SERVER_GROUPS.getNs(), serverGroupKeySet);
                    loadbalancerData.getRelationships().put(INSTANCES.getNs(), instanceKeySet);
                    cachedLoadBalancers.put(loadBalancerKey, loadbalancerData);

                  } else {
                    loadbalancerData = (MutableCacheData) cachedLoadBalancers.get(loadBalancerKey);
                    loadbalancerData
                        .getRelationships()
                        .get(SERVER_GROUPS.getNs())
                        .add(serverGroupKey);
                    loadbalancerData.getRelationships().get(INSTANCES.getNs()).add(instanceKey);
                  }
                }
              });
        });
    logger.info("Caching {} applications in {}", cachedApplications.size(), agentType);
    logger.info("Caching {} clusters in {}", cachedClusters.size(), agentType);
    logger.info("Caching {} server groups in {}", cachedServerGroups.size(), agentType);
    logger.info("Caching {} load balancers in {}", cachedLoadBalancers.size(), agentType);
    logger.info("Caching {} instances in {}", cachedInstances.size(), agentType);

    return new DefaultCacheResult(
        new HashMap<String, Collection<CacheData>>() {
          {
            put(APPLICATIONS.getNs(), cachedApplications.values());
            put(CLUSTERS.getNs(), cachedClusters.values());
            put(SERVER_GROUPS.getNs(), cachedServerGroups.values());
            put(LOAD_BALANCERS.getNs(), cachedLoadBalancers.values());
            put(INSTANCES.getNs(), cachedInstances.values());
            put(ON_DEMAND.getNs(), onDemandKeep.values());
          }
        },
        new HashMap<String, Collection<String>>() {
          {
            put(ON_DEMAND.getNs(), onDemandEvict);
          }
        });
  }

  public Map<Service, List<Revision>> loadServerGroups() {
    Map<Service, List<Revision>> serverGroupsByLoadBalancer = new HashMap<>();
    BatchRequest batch = getCredentials().getCloudRun().batch();
    String project = getCredentials().getProject();
    Optional<CloudRun.Namespaces.Services.List> servicesList = getServicesListRequest(project);
    if (!servicesList.isPresent()) {
      return serverGroupsByLoadBalancer;
    }
    List<Service> loadbalancers = getServicesList(project);
    if (loadbalancers != null && loadbalancers.isEmpty()) {
      return serverGroupsByLoadBalancer;
    }
    Map<String, Service> loadbalancerMap = new HashMap<>();
    Map<String, List<Revision>> lbServerGroupMap = new HashMap<>();
    if (loadbalancers != null) {
      loadbalancers.forEach(lb -> loadbalancerMap.put(lb.getMetadata().getName(), lb));
    }

    JsonBatchCallback<ListRevisionsResponse> callback =
        new JsonBatchCallback<>() {
          @Override
          public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
            String errorJson =
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e);
            logger.error(errorJson);
          }

          @Override
          public void onSuccess(
              ListRevisionsResponse revisionsResponse, HttpHeaders responseHeaders)
              throws IOException {
            List<Revision> revisions = revisionsResponse.getItems();
            if (revisions != null) {
              revisions.forEach(
                  revision -> {
                    String serviceName = CloudrunServerGroup.getServiceName(revision);
                    if (lbServerGroupMap.containsKey(serviceName)) {
                      lbServerGroupMap.get(serviceName).add(revision);
                    } else {
                      List<Revision> revisionSubList = new ArrayList<>();
                      revisionSubList.add(revision);
                      lbServerGroupMap.put(serviceName, revisionSubList);
                    }
                  });
            }
          }
        };
    Optional<CloudRun.Namespaces.Revisions.List> revisionsList = getRevisionsListRequest(project);
    if (revisionsList.isPresent()) {
      try {
        revisionsList.get().queue(batch, callback);
      } catch (IOException e) {
        logger.error("Error in creating request for the method revisions.list !!!");
        return serverGroupsByLoadBalancer;
      }
    }
    try {
      if (batch.size() > 0) {
        batch.execute();
      }
      if (!lbServerGroupMap.isEmpty()) {
        lbServerGroupMap.forEach(
            (svc, rList) -> {
              if (loadbalancerMap.containsKey(svc)) {
                serverGroupsByLoadBalancer.put(loadbalancerMap.get(svc), lbServerGroupMap.get(svc));
              }
            });
      }
    } catch (IOException e) {
      logger.error(
          "Error while fetching Cloudrun Services for the project : {}. {}",
          project,
          e.getMessage());
    }

    return serverGroupsByLoadBalancer;
  }

  public Map<String, Object> loadServerGroupAndLoadBalancer(String serverGroupName) {
    Map<String, Object> serverGroupAndLoadBalancer = new HashMap<>();
    BatchRequest batch = getCredentials().getCloudRun().batch();
    String project = getCredentials().getProject();
    List<Service> loadBalancers = getServicesList(project);
    List<Revision> serverGroups = getRevisionsList(project);
    if (loadBalancers.isEmpty() || serverGroups.isEmpty()) {
      logger.error("No Loadbalancer or server group found !!!!");
      return serverGroupAndLoadBalancer;
    }
    Optional<Revision> serverGroup =
        serverGroups.stream()
            .filter(sg -> sg.getMetadata().getName().equals(serverGroupName))
            .findFirst();
    if (serverGroup.isEmpty()) {
      logger.error("No server group found with name {}", serverGroupName);
      return serverGroupAndLoadBalancer;
    }
    String loadbalancerName = CloudrunServerGroup.getServiceName(serverGroup.get());
    Optional<Service> loadBalancer =
        loadBalancers.stream()
            .filter(lb -> lb.getMetadata().getName().equals(loadbalancerName))
            .findFirst();
    if (loadBalancer.isEmpty()) {
      logger.error(
          "No CloudRun Service found with name {} for the Revision named {}",
          loadbalancerName,
          serverGroupName);
      return serverGroupAndLoadBalancer;
    }
    serverGroupAndLoadBalancer.put("serverGroup", serverGroup.get());
    serverGroupAndLoadBalancer.put("loadBalancer", loadBalancer.get());
    return serverGroupAndLoadBalancer;
  }

  private String getRegion(Revision revision) {
    return revision.getMetadata().getLabels().get(CloudrunServerGroup.getLocationLabel());
  }

  private String getRevisionName(Revision revision) {
    return revision.getMetadata().getName();
  }

  private Optional<CloudRun.Namespaces.Services.List> getServicesListRequest(String project) {
    try {
      return Optional.of(
          getCredentials().getCloudRun().namespaces().services().list(PARENT_PREFIX + project));
    } catch (IOException e) {
      logger.error("Error in creating request for the method services.list !!! {}", e.getMessage());
      return Optional.empty();
    }
  }

  private List<Service> getServicesList(String project) {
    Optional<CloudRun.Namespaces.Services.List> servicesListRequest =
        getServicesListRequest(project);
    if (servicesListRequest.isEmpty()) {
      return new ArrayList<>();
    }
    try {
      return servicesListRequest.get().execute().getItems();
    } catch (IOException e) {
      logger.error("Error executing services.list request. {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  private Optional<CloudRun.Namespaces.Revisions.List> getRevisionsListRequest(String project) {
    try {
      return Optional.of(
          getCredentials().getCloudRun().namespaces().revisions().list(PARENT_PREFIX + project));
    } catch (IOException e) {
      logger.error(
          "Error in creating request for the method revisions.list !!! {} ", e.getMessage());
      return Optional.empty();
    }
  }

  private List<Revision> getRevisionsList(String project) {
    Optional<CloudRun.Namespaces.Revisions.List> revisionsListRequest =
        getRevisionsListRequest(project);
    if (revisionsListRequest.isEmpty()) {
      return new ArrayList<>();
    }
    try {
      return revisionsListRequest.get().execute().getItems();
    } catch (IOException e) {
      logger.error("Error executing revisions.list request. {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  public Map<Revision, String> loadInstances(
      Map<Service, List<Revision>> serverGroupsByLoadBalancer) {
    Map<Revision, String> instancesByServerGroup = new HashMap<>();
    // TODO - check if loadbalancer is needed, if not change the method signature
    serverGroupsByLoadBalancer.forEach(
        (loadBalancer, serverGroups) -> {
          serverGroups.forEach(
              serverGroup -> {
                String serverGroupName = serverGroup.getMetadata().getName();
                instancesByServerGroup.put(serverGroup, serverGroupName + CLOUDRUN_INSTANCE);
              });
        });
    return instancesByServerGroup;
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<Map<String, Object>> requests = new HashSet<>();
    Collection<String> keys = providerCache.getIdentifiers(ON_DEMAND.getNs());
    keys =
        keys.stream()
            .filter(
                k -> {
                  Map<String, String> parse = Keys.parse(k);
                  return (parse != null && Objects.equals(parse.get("account"), getAccountName()));
                })
            .collect(Collectors.toSet());
    providerCache
        .getAll(ON_DEMAND.getNs(), keys)
        .forEach(
            cacheData -> {
              Map<String, String> details = Keys.parse(cacheData.getId());
              requests.add(
                  Map.of(
                      "details", details,
                      "moniker", convertOnDemandDetails(details),
                      "cacheTime", cacheData.getAttributes().get("cacheTime"),
                      "processedCount", cacheData.getAttributes().get("processedCount"),
                      "processedTime", cacheData.getAttributes().get("processedTime")));
            });
    return requests;
  }

  private static <T> T setGroovyRef(Reference<T> ref, T newValue) {
    ref.set(newValue);
    return newValue;
  }
}
