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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SERVER_GROUPS;
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.BACKEND_SERVICE_NAMES;
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.GLOBAL_LOAD_BALANCER_NAMES;
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.LOAD_BALANCING_POLICY;
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.REGIONAL_LOAD_BALANCER_NAMES;
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.REGION_BACKEND_SERVICE_NAMES;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Autoscaler;
import com.google.api.services.compute.model.AutoscalerStatusDetails;
import com.google.api.services.compute.model.AutoscalingPolicy;
import com.google.api.services.compute.model.AutoscalingPolicyCpuUtilization;
import com.google.api.services.compute.model.AutoscalingPolicyCustomMetricUtilization;
import com.google.api.services.compute.model.AutoscalingPolicyLoadBalancingUtilization;
import com.google.api.services.compute.model.AutoscalingPolicyScaleInControl;
import com.google.api.services.compute.model.DistributionPolicy;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.api.services.compute.model.NamedPort;
import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.frigga.ami.AppVersion;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.DefaultJsonCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider;
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder.CacheDataBuilder;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.compute.BatchComputeRequest;
import com.netflix.spinnaker.clouddriver.google.compute.BatchPaginatedComputeRequest;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.compute.InstanceTemplates;
import com.netflix.spinnaker.clouddriver.google.compute.Instances;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.AutoscalingMode;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CpuUtilization;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CpuUtilization.PredictiveMethod;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CustomMetricUtilization;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CustomMetricUtilization.UtilizationTargetType;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.FixedOrPercent;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.LoadBalancingUtilization;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.ScaleInControl;
import com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance;
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstances;
import com.netflix.spinnaker.clouddriver.google.model.GoogleLabeledResource;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy;
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ParametersAreNonnullByDefault
abstract class AbstractGoogleServerGroupCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware {

  private static final ImmutableSet<AgentDataType> DATA_TYPES =
      ImmutableSet.of(
          AUTHORITATIVE.forType(SERVER_GROUPS.getNs()),
          AUTHORITATIVE.forType(APPLICATIONS.getNs()),
          AUTHORITATIVE.forType(CLUSTERS.getNs()),
          INFORMATIVE.forType(LOAD_BALANCERS.getNs()));

  private static final String ON_DEMAND_TYPE =
      String.join(":", GoogleCloudProvider.getID(), OnDemandType.ServerGroup.getValue());

  private static final Splitter COMMA = Splitter.on(',').omitEmptyStrings().trimResults();
  private static final MapSplitter IMAGE_DESCRIPTION_SPLITTER =
      Splitter.on(',').withKeyValueSeparator(": ");

  private final GoogleNamedAccountCredentials credentials;
  private final GoogleComputeApiFactory computeApiFactory;
  private final String region;
  private final OnDemandMetricsSupport onDemandMetricsSupport;
  private final ObjectMapper objectMapper;
  private final Namer<GoogleLabeledResource> naming;

  AbstractGoogleServerGroupCachingAgent(
      GoogleNamedAccountCredentials credentials,
      GoogleComputeApiFactory computeApiFactory,
      Registry registry,
      String region,
      ObjectMapper objectMapper) {
    this.credentials = credentials;
    this.computeApiFactory = computeApiFactory;
    this.region = region;
    this.onDemandMetricsSupport = new OnDemandMetricsSupport(registry, this, ON_DEMAND_TYPE);
    this.objectMapper = objectMapper;
    this.naming =
        NamerRegistry.lookup()
            .withProvider(GoogleCloudProvider.getID())
            .withAccount(credentials.getName())
            .withResource(GoogleLabeledResource.class);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {

    try {
      CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(DATA_TYPES);
      cacheResultBuilder.setStartTime(System.currentTimeMillis());

      List<GoogleServerGroup> serverGroups = getServerGroups(providerCache);

      // If an entry in ON_DEMAND was generated _after_ we started our caching run, add it to the
      // cacheResultBuilder, since we may use it in buildCacheResult.
      //
      // We don't evict things unless they've been processed because Orca, after sending an
      // on-demand cache refresh, doesn't consider the request "finished" until it calls
      // pendingOnDemandRequests and sees a processedCount of 1. In a saner world, Orca would
      // probably just trust that if the key wasn't returned by pendingOnDemandRequests, it must
      // have been processed. But we don't live in that world.
      Set<String> serverGroupKeys =
          serverGroups.stream().map(this::getServerGroupKey).collect(toImmutableSet());
      providerCache
          .getAll(ON_DEMAND.getNs(), serverGroupKeys)
          .forEach(
              cacheData -> {
                long cacheTime = (long) cacheData.getAttributes().get("cacheTime");
                if (cacheTime < cacheResultBuilder.getStartTime()
                    && (int) cacheData.getAttributes().get("processedCount") > 0) {
                  cacheResultBuilder.getOnDemand().getToEvict().add(cacheData.getId());
                } else {
                  cacheResultBuilder.getOnDemand().getToKeep().put(cacheData.getId(), cacheData);
                }
              });

      CacheResult cacheResult = buildCacheResult(cacheResultBuilder, serverGroups);

      // For all the ON_DEMAND entries that we marked as 'toKeep' earlier, here we mark them as
      // processed so that they get evicted in future calls to this method. Why can't we just mark
      // them as evicted here, though? Why wait for another run?
      cacheResult
          .getCacheResults()
          .get(ON_DEMAND.getNs())
          .forEach(
              cacheData -> {
                cacheData.getAttributes().put("processedTime", System.currentTimeMillis());
                int processedCount = (Integer) cacheData.getAttributes().get("processedCount");
                cacheData.getAttributes().put("processedCount", processedCount + 1);
              });

      return cacheResult;
    } catch (IOException e) {
      // CatsOnDemandCacheUpdater handles this
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return OnDemandType.ServerGroup.equals(type)
        && GoogleCloudProvider.getID().equals(cloudProvider);
  }

  @Nullable
  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {

    try {
      String serverGroupName = (String) data.get("serverGroupName");
      if (serverGroupName == null
          || !getAccountName().equals(data.get("account"))
          || !region.equals(data.get("region"))) {
        return null;
      }

      Optional<GoogleServerGroup> serverGroup =
          getMetricsSupport().readData(() -> getServerGroup(serverGroupName, providerCache));

      CacheResultBuilder cacheResultBuilder = new CacheResultBuilder();

      if (serverGroup.isPresent()) {
        String serverGroupKey = getServerGroupKey(serverGroup.get());
        CacheResult result =
            getMetricsSupport()
                .transformData(
                    () ->
                        buildCacheResult(cacheResultBuilder, ImmutableList.of(serverGroup.get())));
        String cacheResults = objectMapper.writeValueAsString(result.getCacheResults());
        CacheData cacheData =
            getMetricsSupport()
                .onDemandStore(
                    () ->
                        new DefaultCacheData(
                            serverGroupKey,
                            /* ttlSeconds= */ (int) Duration.ofMinutes(10).getSeconds(),
                            ImmutableMap.of(
                                "cacheTime",
                                System.currentTimeMillis(),
                                "cacheResults",
                                cacheResults,
                                "processedCount",
                                0),
                            /* relationships= */ ImmutableMap.of()));
        providerCache.putCacheData(ON_DEMAND.getNs(), cacheData);
        return new OnDemandResult(
            getOnDemandAgentType(), result, /* evictions= */ ImmutableMap.of());
      } else {
        Collection<String> existingIdentifiers =
            getOnDemandKeysToEvictForMissingServerGroup(providerCache, serverGroupName);
        providerCache.evictDeletedItems(ON_DEMAND.getNs(), existingIdentifiers);
        return new OnDemandResult(
            getOnDemandAgentType(),
            new DefaultCacheResult(ImmutableMap.of()),
            ImmutableMap.of(SERVER_GROUPS.getNs(), ImmutableList.copyOf(existingIdentifiers)));
      }
    } catch (IOException e) {
      // CatsOnDemandCacheUpdater handles this
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Return the keys that will be evicted from the on-demand cache if the given serverGroupName
   * can't be found in the region.
   */
  abstract Collection<String> getOnDemandKeysToEvictForMissingServerGroup(
      ProviderCache providerCache, String serverGroupName);

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    List<String> ownedKeys =
        providerCache.getIdentifiers(ON_DEMAND.getNs()).stream()
            .filter(this::keyOwnedByThisAgent)
            .collect(toImmutableList());

    return providerCache.getAll(ON_DEMAND.getNs(), ownedKeys).stream()
        .map(
            cacheData -> {
              Map<String, Object> map = new HashMap<>();
              map.put("details", Keys.parse(cacheData.getId()));
              map.put("moniker", cacheData.getAttributes().get("moniker"));
              map.put("cacheTime", cacheData.getAttributes().get("cacheTime"));
              map.put("processedCount", cacheData.getAttributes().get("processedCount"));
              map.put("processedTime", cacheData.getAttributes().get("processedTime"));
              return map;
            })
        .collect(toImmutableList());
  }

  private boolean keyOwnedByThisAgent(String key) {
    Map<String, String> parsedKey = Keys.parse(key);
    return parsedKey != null
        && parsedKey.get("type").equals(SERVER_GROUPS.getNs())
        && keyOwnedByThisAgent(parsedKey);
  }

  /**
   * Return whether or not the parsed key data (as returned from {@link
   * Keys#parseKey(java.lang.String)} is a key that is "owned" by this caching agent. The key type
   * will be checked before this method is called.
   */
  abstract boolean keyOwnedByThisAgent(Map<String, String> parsedKey);

  private CacheResult buildCacheResult(
      CacheResultBuilder cacheResultBuilder, List<GoogleServerGroup> serverGroups) {

    try {
      for (GoogleServerGroup serverGroup : serverGroups) {

        Moniker moniker = naming.deriveMoniker(serverGroup);

        String applicationKey = Keys.getApplicationKey(moniker.getApp());
        String clusterKey =
            Keys.getClusterKey(getAccountName(), moniker.getApp(), moniker.getCluster());
        String serverGroupKey = getServerGroupKey(serverGroup);
        Set<String> instanceKeys =
            serverGroup.getInstances().stream()
                .map(instance -> Keys.getInstanceKey(getAccountName(), region, instance.getName()))
                .collect(toImmutableSet());

        CacheDataBuilder application =
            cacheResultBuilder.namespace(APPLICATIONS.getNs()).keep(applicationKey);
        application.getAttributes().put("name", moniker.getApp());
        application.getRelationships().get(CLUSTERS.getNs()).add(clusterKey);
        application.getRelationships().get(INSTANCES.getNs()).addAll(instanceKeys);

        CacheDataBuilder cluster = cacheResultBuilder.namespace(CLUSTERS.getNs()).keep(clusterKey);
        cluster.getAttributes().put("name", moniker.getCluster());
        cluster.getAttributes().put("accountName", getAccountName());
        cluster.getAttributes().put("moniker", moniker);
        cluster.getRelationships().get(APPLICATIONS.getNs()).add(applicationKey);
        cluster.getRelationships().get(SERVER_GROUPS.getNs()).add(serverGroupKey);
        cluster.getRelationships().get(INSTANCES.getNs()).addAll(instanceKeys);

        Set<String> loadBalancerKeys = getLoadBalancerKeys(serverGroup);
        loadBalancerKeys.forEach(
            key ->
                cacheResultBuilder
                    .namespace(LOAD_BALANCERS.getNs())
                    .keep(key)
                    .getRelationships()
                    .get(SERVER_GROUPS.getNs())
                    .add(serverGroupKey));

        if (shouldUseOnDemandData(cacheResultBuilder, serverGroupKey)) {
          moveOnDemandDataToNamespace(cacheResultBuilder, serverGroup);
        } else {
          CacheDataBuilder serverGroupCacheData =
              cacheResultBuilder.namespace(SERVER_GROUPS.getNs()).keep(serverGroupKey);
          serverGroupCacheData.setAttributes(
              objectMapper.convertValue(serverGroup, new TypeReference<Map<String, Object>>() {}));
          serverGroupCacheData.getRelationships().get(APPLICATIONS.getNs()).add(applicationKey);
          serverGroupCacheData.getRelationships().get(CLUSTERS.getNs()).add(clusterKey);
          serverGroupCacheData
              .getRelationships()
              .get(LOAD_BALANCERS.getNs())
              .addAll(loadBalancerKeys);
          serverGroupCacheData.getRelationships().get(INSTANCES.getNs()).addAll(instanceKeys);
        }
      }
    } catch (IOException e) {
      // CatsOnDemandCacheUpdater handles this
      throw new UncheckedIOException(e);
    }

    return cacheResultBuilder.build();
  }

  private ImmutableSet<String> getLoadBalancerKeys(GoogleServerGroup serverGroup) {
    ImmutableSet.Builder<String> loadBalancerKeys = ImmutableSet.builder();
    nullableStream((Collection<String>) serverGroup.getAsg().get(REGIONAL_LOAD_BALANCER_NAMES))
        .map(name -> Keys.getLoadBalancerKey(region, getAccountName(), name))
        .forEach(loadBalancerKeys::add);
    nullableStream((Collection<String>) serverGroup.getAsg().get(GLOBAL_LOAD_BALANCER_NAMES))
        .map(name -> Keys.getLoadBalancerKey("global", getAccountName(), name))
        .forEach(loadBalancerKeys::add);
    return loadBalancerKeys.build();
  }

  private static <T> Stream<T> nullableStream(@Nullable Collection<T> collection) {
    return Optional.ofNullable(collection).orElse(ImmutableList.of()).stream();
  }

  private static boolean shouldUseOnDemandData(
      CacheResultBuilder cacheResultBuilder, String serverGroupKey) {
    CacheData cacheData = cacheResultBuilder.getOnDemand().getToKeep().get(serverGroupKey);
    return cacheData != null
        && (long) cacheData.getAttributes().get("cacheTime") > cacheResultBuilder.getStartTime();
  }

  private void moveOnDemandDataToNamespace(
      CacheResultBuilder cacheResultBuilder, GoogleServerGroup serverGroup) throws IOException {

    String serverGroupKey = getServerGroupKey(serverGroup);
    Map<String, List<DefaultJsonCacheData>> onDemandData =
        objectMapper.readValue(
            (String)
                cacheResultBuilder
                    .getOnDemand()
                    .getToKeep()
                    .get(serverGroupKey)
                    .getAttributes()
                    .get("cacheResults"),
            new TypeReference<Map<String, List<DefaultJsonCacheData>>>() {});
    onDemandData.forEach(
        (namespace, cacheDatas) -> {
          if (namespace.equals(ON_DEMAND.getNs())) {
            return;
          }

          cacheDatas.forEach(
              cacheData -> {
                CacheDataBuilder cacheDataBuilder =
                    cacheResultBuilder.namespace(namespace).keep(cacheData.getId());
                cacheDataBuilder.setAttributes(cacheData.getAttributes());
                cacheDataBuilder.setRelationships(
                    Utils.mergeOnDemandCacheRelationships(
                        cacheData.getRelationships(), cacheDataBuilder.getRelationships()));
                cacheResultBuilder.getOnDemand().getToKeep().remove(cacheData.getId());
              });
        });
  }

  private String getServerGroupKey(GoogleServerGroup serverGroup) {
    return Keys.getServerGroupKey(
        serverGroup.getName(),
        naming.deriveMoniker(serverGroup).getCluster(),
        getAccountName(),
        region,
        serverGroup.getZone());
  }

  private List<GoogleServerGroup> getServerGroups(ProviderCache providerCache) throws IOException {

    ImmutableList<GoogleInstance> instances =
        retrieveAllInstancesInRegion().stream()
            .map(instance -> GoogleInstances.createFromComputeInstance(instance, credentials))
            .collect(toImmutableList());
    return constructServerGroups(
        providerCache,
        retrieveInstanceGroupManagers(),
        instances,
        retrieveInstanceTemplates(),
        retrieveAutoscalers());
  }

  /**
   * Return all the instance group managers in this region that are handled by this caching agent.
   */
  abstract Collection<InstanceGroupManager> retrieveInstanceGroupManagers() throws IOException;

  /**
   * Return all the autoscalers in this region that might apply to instance group managers returned
   * from {@link #retrieveInstanceGroupManagers()}.
   */
  abstract Collection<Autoscaler> retrieveAutoscalers() throws IOException;

  private Optional<GoogleServerGroup> getServerGroup(String name, ProviderCache providerCache) {

    InstanceTemplates instanceTemplatesApi = computeApiFactory.createInstanceTemplates(credentials);

    try {
      Optional<InstanceGroupManager> managerOpt = retrieveInstanceGroupManager(name);

      if (!managerOpt.isPresent()) {
        return Optional.empty();
      }

      InstanceGroupManager manager = managerOpt.get();

      List<GoogleInstance> instances =
          retrieveRelevantInstances(manager).stream()
              .map(instance -> GoogleInstances.createFromComputeInstance(instance, credentials))
              .collect(toImmutableList());

      List<Autoscaler> autoscalers =
          retrieveAutoscaler(manager).map(ImmutableList::of).orElse(ImmutableList.of());

      List<InstanceTemplate> instanceTemplates = new ArrayList<>();
      if (manager.getInstanceTemplate() != null) {
        instanceTemplatesApi
            .get(Utils.getLocalName(manager.getInstanceTemplate()))
            .executeGet()
            .ifPresent(instanceTemplates::add);
      }

      return constructServerGroups(
              providerCache, ImmutableList.of(manager), instances, instanceTemplates, autoscalers)
          .stream()
          .findAny();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Retrieve the instance group manager named {@code name} that is managed by this caching agent.
   */
  abstract Optional<InstanceGroupManager> retrieveInstanceGroupManager(String name)
      throws IOException;

  /** Retrieve the autoscaler that handles scaling for {@code manager}. */
  abstract Optional<Autoscaler> retrieveAutoscaler(InstanceGroupManager manager) throws IOException;

  /**
   * Retrieve all instances in this region that <em>may</em> be managed by {@code manager}. A later
   * step will winnow these down, so this should be a superset of those instances.
   */
  abstract Collection<Instance> retrieveRelevantInstances(InstanceGroupManager manager)
      throws IOException;

  @Value
  private static class TargetAndScope {
    String target;
    @Nullable String region;
    @Nullable String zone;

    static TargetAndScope forAutoscaler(Autoscaler autoscaler) {
      return new TargetAndScope(
          Utils.getLocalName(autoscaler.getTarget()),
          Utils.getLocalName(autoscaler.getRegion()),
          Utils.getLocalName(autoscaler.getZone()));
    }

    static TargetAndScope forInstanceGroupManager(InstanceGroupManager manager) {
      return new TargetAndScope(
          manager.getName(),
          Utils.getLocalName(manager.getRegion()),
          Utils.getLocalName(manager.getZone()));
    }
  }

  private List<GoogleServerGroup> constructServerGroups(
      ProviderCache providerCache,
      Collection<InstanceGroupManager> managers,
      Collection<GoogleInstance> instances,
      Collection<InstanceTemplate> instanceTemplates,
      Collection<Autoscaler> autoscalers) {

    Map<TargetAndScope, Autoscaler> autoscalerMap =
        autoscalers.stream()
            .collect(toImmutableMap(TargetAndScope::forAutoscaler, scaler -> scaler));
    Map<String, InstanceTemplate> instanceTemplatesMap =
        instanceTemplates.stream().collect(toImmutableMap(InstanceTemplate::getName, i -> i));
    return managers.stream()
        .map(
            manager -> {
              ImmutableSet<GoogleInstance> ownedInstances = ImmutableSet.of();
              if (manager.getBaseInstanceName() != null) {
                ownedInstances =
                    instances.stream()
                        .filter(
                            instance ->
                                instance.getName().startsWith(manager.getBaseInstanceName()))
                        .filter(instance -> instanceScopedToManager(instance, manager))
                        .collect(toImmutableSet());
              }
              TargetAndScope key = TargetAndScope.forInstanceGroupManager(manager);
              Autoscaler autoscaler = autoscalerMap.get(key);
              InstanceTemplate instanceTemplate =
                  instanceTemplatesMap.get(Utils.getLocalName(manager.getInstanceTemplate()));
              return createServerGroup(
                  manager, ownedInstances, instanceTemplate, autoscaler, providerCache);
            })
        .collect(toImmutableList());
  }

  private boolean instanceScopedToManager(GoogleInstance instance, InstanceGroupManager manager) {
    if (manager.getZone() == null) {
      // For a regional manager, all zones are in scope. (All zones in the region, anyway, which are
      // the only instances we retrieved.)
      return true;
    } else {
      return Utils.getLocalName(manager.getZone()).equals(instance.getZone());
    }
  }

  private GoogleServerGroup createServerGroup(
      InstanceGroupManager manager,
      ImmutableSet<GoogleInstance> instances,
      @Nullable InstanceTemplate instanceTemplate,
      @Nullable Autoscaler autoscaler,
      ProviderCache providerCache) {

    GoogleServerGroup serverGroup = new GoogleServerGroup();
    serverGroup.setName(manager.getName());
    setRegionConfig(serverGroup, manager);
    serverGroup.setAccount(getAccountName());
    serverGroup.setInstances(instances);
    serverGroup.setNamedPorts(convertNamedPorts(manager));
    serverGroup.setSelfLink(manager.getSelfLink());
    serverGroup.setCurrentActions(manager.getCurrentActions());

    setLaunchConfig(serverGroup, manager, instanceTemplate, providerCache);
    setAutoscalerGroup(serverGroup, manager, instanceTemplate);
    if (instanceTemplate != null) {
      InstanceProperties properties = instanceTemplate.getProperties();
      if (properties != null) {
        serverGroup.setCanIpForward(properties.getCanIpForward());
        if (properties.getServiceAccounts() != null) {
          serverGroup.setInstanceTemplateServiceAccounts(
              ImmutableSet.copyOf(properties.getServiceAccounts()));
        }
        if (properties.getTags() != null && properties.getTags().getItems() != null) {
          serverGroup.setInstanceTemplateTags(ImmutableSet.copyOf(properties.getTags().getItems()));
        }
        if (properties.getLabels() != null) {
          serverGroup.setInstanceTemplateLabels(ImmutableMap.copyOf(properties.getLabels()));
        }
        if (properties.getNetworkInterfaces() != null
            && !properties.getNetworkInterfaces().isEmpty()
            && properties.getNetworkInterfaces().get(0) != null) {
          serverGroup.setNetworkName(
              Utils.decorateXpnResourceIdIfNeeded(
                  credentials.getProject(), properties.getNetworkInterfaces().get(0).getNetwork()));
        }
      }
    }
    serverGroup.setStatefulPolicy(manager.getStatefulPolicy());
    if (manager.getAutoHealingPolicies() != null && !manager.getAutoHealingPolicies().isEmpty()) {
      serverGroup.setAutoHealingPolicy(manager.getAutoHealingPolicies().get(0));
    }
    populateAutoscaler(serverGroup, autoscaler);
    return serverGroup;
  }

  private void setRegionConfig(GoogleServerGroup serverGroup, InstanceGroupManager manager) {

    serverGroup.setRegional(manager.getZone() == null);

    if (serverGroup.getRegional()) {
      serverGroup.setRegion(Utils.getLocalName(manager.getRegion()));
      DistributionPolicy distributionPolicy = manager.getDistributionPolicy();
      ImmutableList<String> zones = getZones(distributionPolicy);
      serverGroup.setZones(ImmutableSet.copyOf(zones));
      serverGroup.setDistributionPolicy(
          new GoogleDistributionPolicy(zones, getTargetShape(distributionPolicy)));
    } else {
      String zone = Utils.getLocalName(manager.getZone());
      serverGroup.setZone(zone);
      serverGroup.setZones(ImmutableSet.of(zone));
      serverGroup.setRegion(credentials.regionFromZone(zone));
    }
  }

  private static ImmutableList<String> getZones(@Nullable DistributionPolicy distributionPolicy) {
    if (distributionPolicy == null || distributionPolicy.getZones() == null) {
      return ImmutableList.of();
    }
    return distributionPolicy.getZones().stream()
        .map(z -> Utils.getLocalName(z.getZone()))
        .collect(toImmutableList());
  }

  @Nullable
  private static String getTargetShape(@Nullable DistributionPolicy distributionPolicy) {
    if (distributionPolicy == null) {
      return null;
    }
    return distributionPolicy.getTargetShape();
  }

  @Nullable
  private static ImmutableMap<String, Integer> convertNamedPorts(InstanceGroupManager manager) {
    if (manager.getNamedPorts() == null) {
      return null;
    }
    return manager.getNamedPorts().stream()
        .filter(namedPort -> namedPort.getName() != null)
        .filter(namedPort -> namedPort.getPort() != null)
        .collect(toImmutableMap(NamedPort::getName, NamedPort::getPort));
  }

  private void setLaunchConfig(
      GoogleServerGroup serverGroup,
      InstanceGroupManager manager,
      @Nullable InstanceTemplate instanceTemplate,
      ProviderCache providerCache) {

    HashMap<String, Object> launchConfig = new HashMap<>();
    launchConfig.put("createdTime", Utils.getTimeFromTimestamp(manager.getCreationTimestamp()));

    if (instanceTemplate != null) {
      launchConfig.put("launchConfigurationName", instanceTemplate.getName());
      launchConfig.put("instanceTemplate", instanceTemplate);
      if (instanceTemplate.getProperties() != null) {
        List<AttachedDisk> disks = getDisks(instanceTemplate);
        instanceTemplate.getProperties().setDisks(disks);
        if (instanceTemplate.getProperties().getMachineType() != null) {
          launchConfig.put("instanceType", instanceTemplate.getProperties().getMachineType());
        }
        if (instanceTemplate.getProperties().getMinCpuPlatform() != null) {
          launchConfig.put("minCpuPlatform", instanceTemplate.getProperties().getMinCpuPlatform());
        }
        setSourceImage(serverGroup, launchConfig, disks, providerCache);
      }
    }
    serverGroup.setLaunchConfig(copyToImmutableMapWithoutNullValues(launchConfig));
  }

  private static ImmutableList<AttachedDisk> getDisks(InstanceTemplate template) {

    if (template.getProperties() == null || template.getProperties().getDisks() == null) {
      return ImmutableList.of();
    }
    List<AttachedDisk> persistentDisks =
        template.getProperties().getDisks().stream()
            .filter(disk -> "PERSISTENT".equals(disk.getType()))
            .collect(toImmutableList());

    if (persistentDisks.isEmpty() || persistentDisks.get(0).getBoot()) {
      return ImmutableList.copyOf(template.getProperties().getDisks());
    }

    ImmutableList.Builder<AttachedDisk> sortedDisks = ImmutableList.builder();
    Optional<AttachedDisk> firstBootDisk =
        persistentDisks.stream().filter(AttachedDisk::getBoot).findFirst();
    firstBootDisk.ifPresent(sortedDisks::add);
    template.getProperties().getDisks().stream()
        .filter(disk -> !disk.getBoot())
        .forEach(sortedDisks::add);
    return sortedDisks.build();
  }

  private void setSourceImage(
      GoogleServerGroup serverGroup,
      Map<String, Object> launchConfig,
      List<AttachedDisk> disks,
      ProviderCache providerCache) {

    if (disks.isEmpty()) {
      return;
    }
    // Disks were sorted so boot disk comes first
    AttachedDisk firstDisk = disks.get(0);
    if (!firstDisk.getBoot()) {
      return;
    }

    if (firstDisk.getInitializeParams() != null
        && firstDisk.getInitializeParams().getSourceImage() != null) {
      String sourceImage = Utils.getLocalName(firstDisk.getInitializeParams().getSourceImage());
      launchConfig.put("imageId", sourceImage);
      String imageKey = Keys.getImageKey(getAccountName(), sourceImage);
      CacheData image = providerCache.get(IMAGES.getNs(), imageKey);
      if (image != null) {
        String description =
            (String) ((Map<String, Object>) image.getAttributes().get("image")).get("description");
        ImmutableMap<String, Object> buildInfo = createBuildInfo(description);
        if (buildInfo != null) {
          serverGroup.setBuildInfo(buildInfo);
        }
      }
    }
  }

  @Nullable
  private static ImmutableMap<String, Object> createBuildInfo(@Nullable String imageDescription) {
    if (imageDescription == null) {
      return null;
    }
    Map<String, String> tags;
    try {
      tags = IMAGE_DESCRIPTION_SPLITTER.split(imageDescription);
    } catch (IllegalArgumentException e) {
      return null;
    }
    if (!tags.containsKey("appversion")) {
      return null;
    }
    AppVersion appversion = AppVersion.parseName(tags.get("appversion"));
    if (appversion == null) {
      return null;
    }
    Map<String, Object> buildInfo = new HashMap<>();
    buildInfo.put("package_name", appversion.getPackageName());
    buildInfo.put("version", appversion.getVersion());
    buildInfo.put("commit", appversion.getCommit());
    if (appversion.getBuildJobName() != null) {
      Map<String, String> jenkinsInfo = new HashMap<>();
      jenkinsInfo.put("name", appversion.getBuildJobName());
      jenkinsInfo.put("number", appversion.getBuildNumber());
      if (tags.containsKey("build_host")) {
        jenkinsInfo.put("host", tags.get("build_host"));
      }
      buildInfo.put("jenkins", copyToImmutableMap((jenkinsInfo)));
    }
    if (tags.containsKey("build_info_url")) {
      buildInfo.put("buildInfoUrl", tags.get("build_info_url"));
    }
    return copyToImmutableMap(buildInfo);
  }

  private static <K, V> ImmutableMap<K, V> copyToImmutableMap(Map<K, V> map) {
    return map.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private void setAutoscalerGroup(
      GoogleServerGroup serverGroup,
      InstanceGroupManager manager,
      @Nullable InstanceTemplate instanceTemplate) {

    Map<String, Object> autoscalerGroup = new HashMap<>();

    if (manager.getTargetSize() != null) {
      autoscalerGroup.put("minSize", manager.getTargetSize());
      autoscalerGroup.put("maxSize", manager.getTargetSize());
      autoscalerGroup.put("desiredCapacity", manager.getTargetSize());
    }

    if (instanceTemplate != null
        && instanceTemplate.getProperties() != null
        && instanceTemplate.getProperties().getMetadata() != null
        && instanceTemplate.getProperties().getMetadata().getItems() != null) {

      ImmutableMap<String, String> metadata =
          instanceTemplate.getProperties().getMetadata().getItems().stream()
              .filter(item -> item.getKey() != null)
              .filter(item -> item.getValue() != null)
              .collect(toImmutableMap(Items::getKey, Items::getValue));

      if (metadata.containsKey(GLOBAL_LOAD_BALANCER_NAMES)) {
        autoscalerGroup.put(
            GLOBAL_LOAD_BALANCER_NAMES,
            COMMA.splitToList(metadata.get(GLOBAL_LOAD_BALANCER_NAMES)));
      }

      if (metadata.containsKey(REGIONAL_LOAD_BALANCER_NAMES)) {
        autoscalerGroup.put(
            REGIONAL_LOAD_BALANCER_NAMES,
            COMMA.splitToList(metadata.get(REGIONAL_LOAD_BALANCER_NAMES)));
        List<String> loadBalancerNames =
            Utils.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(manager.getTargetPools());

        // The isDisabled property of a server group is set based on whether there are associated
        // target pools,
        // and whether the metadata of the server group contains a list of load balancers to
        // actually
        // associate
        // the server group with.
        // We set the disabled state for L4 lBs here (before writing into the cache) and calculate
        // the L7 disabled state when we read the server groups from the cache.
        serverGroup.setDisabled(loadBalancerNames.isEmpty());
      }

      if (metadata.containsKey(BACKEND_SERVICE_NAMES)) {
        autoscalerGroup.put(
            BACKEND_SERVICE_NAMES, COMMA.splitToList(metadata.get(BACKEND_SERVICE_NAMES)));
      }

      if (metadata.containsKey(REGION_BACKEND_SERVICE_NAMES)) {
        autoscalerGroup.put(
            REGION_BACKEND_SERVICE_NAMES,
            COMMA.splitToList(metadata.get(REGION_BACKEND_SERVICE_NAMES)));
      }

      if (metadata.containsKey(LOAD_BALANCING_POLICY)) {
        try {
          autoscalerGroup.put(
              LOAD_BALANCING_POLICY,
              objectMapper.readValue(
                  metadata.get(LOAD_BALANCING_POLICY), GoogleHttpLoadBalancingPolicy.class));
        } catch (IOException e) {
          log.warn("Error parsing load balancing policy", e);
        }
      }
    }

    serverGroup.setAsg(copyToImmutableMapWithoutNullValues(autoscalerGroup));
  }

  private static void populateAutoscaler(
      GoogleServerGroup serverGroup, @Nullable Autoscaler autoscaler) {

    if (autoscaler == null) {
      return;
    }

    AutoscalingPolicy autoscalingPolicy = autoscaler.getAutoscalingPolicy();
    if (autoscalingPolicy != null) {
      serverGroup.setAutoscalingPolicy(convertAutoscalingPolicy(autoscalingPolicy));
      // is asg possibly null???
      HashMap<String, Object> autoscalingGroup = new HashMap<>(serverGroup.getAsg());
      autoscalingGroup.put("minSize", autoscalingPolicy.getMinNumReplicas());
      autoscalingGroup.put("maxSize", autoscalingPolicy.getMaxNumReplicas());
      serverGroup.setAsg(copyToImmutableMapWithoutNullValues(autoscalingGroup));
    }
    if (autoscaler.getStatusDetails() != null) {
      serverGroup.setAutoscalingMessages(
          autoscaler.getStatusDetails().stream()
              .map(AutoscalerStatusDetails::getMessage)
              .filter(Objects::nonNull)
              .collect(toImmutableList()));
    }
  }

  private static GoogleAutoscalingPolicy convertAutoscalingPolicy(AutoscalingPolicy input) {
    CpuUtilization cpu = convertCpuUtilization(input.getCpuUtilization());
    LoadBalancingUtilization loadBalancing =
        convertLoadBalancingUtilization(input.getLoadBalancingUtilization());
    List<CustomMetricUtilization> customMetrics =
        convertCustomMetricUtilizations(input.getCustomMetricUtilizations());
    GoogleAutoscalingPolicy output = new GoogleAutoscalingPolicy();
    output.setCoolDownPeriodSec(input.getCoolDownPeriodSec());
    output.setCpuUtilization(cpu);
    output.setCustomMetricUtilizations(customMetrics);
    output.setLoadBalancingUtilization(loadBalancing);
    output.setMaxNumReplicas(input.getMaxNumReplicas());
    output.setMinNumReplicas(input.getMinNumReplicas());
    output.setMode(valueOf(AutoscalingMode.class, input.getMode()));
    output.setScaleInControl(convertScaleInControl(input.getScaleInControl()));
    return output;
  }

  @Nullable
  private static CpuUtilization convertCpuUtilization(
      @Nullable AutoscalingPolicyCpuUtilization input) {
    if (input == null) {
      return null;
    }
    CpuUtilization output = new CpuUtilization();
    output.setUtilizationTarget(input.getUtilizationTarget());
    output.setPredictiveMethod(valueOf(PredictiveMethod.class, input.getPredictiveMethod()));
    return output;
  }

  @Nullable
  private static LoadBalancingUtilization convertLoadBalancingUtilization(
      @Nullable AutoscalingPolicyLoadBalancingUtilization input) {
    if (input == null) {
      return null;
    }
    LoadBalancingUtilization output = new LoadBalancingUtilization();
    output.setUtilizationTarget(input.getUtilizationTarget());
    return output;
  }

  @Nullable
  private static ImmutableList<CustomMetricUtilization> convertCustomMetricUtilizations(
      @Nullable List<AutoscalingPolicyCustomMetricUtilization> input) {
    if (input == null) {
      return null;
    }
    return input.stream()
        .map(AbstractGoogleServerGroupCachingAgent::convertCustomMetricUtilization)
        .collect(toImmutableList());
  }

  private static CustomMetricUtilization convertCustomMetricUtilization(
      AutoscalingPolicyCustomMetricUtilization input) {
    CustomMetricUtilization output = new CustomMetricUtilization();
    output.setMetric(input.getMetric());
    output.setUtilizationTarget(input.getUtilizationTarget());
    output.setUtilizationTargetType(
        valueOf(UtilizationTargetType.class, input.getUtilizationTargetType()));
    return output;
  }

  private static ScaleInControl convertScaleInControl(
      @Nullable AutoscalingPolicyScaleInControl input) {
    if (input == null) {
      return null;
    }
    FixedOrPercent maxScaledInReplicas = null;
    if (input.getMaxScaledInReplicas() != null) {
      maxScaledInReplicas = new FixedOrPercent();
      maxScaledInReplicas.setFixed(input.getMaxScaledInReplicas().getFixed());
      maxScaledInReplicas.setPercent(input.getMaxScaledInReplicas().getPercent());
    }
    ScaleInControl output = new ScaleInControl();
    output.setTimeWindowSec(input.getTimeWindowSec());
    output.setMaxScaledInReplicas(maxScaledInReplicas);
    return output;
  }

  private static <T extends Enum<T>> T valueOf(Class<T> enumType, @Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static <K, V> ImmutableMap<K, V> copyToImmutableMapWithoutNullValues(Map<K, V> map) {
    return map.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  Collection<Instance> retrieveAllInstancesInRegion() throws IOException {

    Instances instancesApi = computeApiFactory.createInstances(credentials);
    BatchPaginatedComputeRequest<Compute.Instances.List, Instance> instancesRequest =
        computeApiFactory.createPaginatedBatchRequest(credentials);

    getZonesForRegion().forEach(zone -> instancesRequest.queue(instancesApi.list(zone)));

    return instancesRequest.execute(getBatchContext(".instance"));
  }

  private Collection<InstanceTemplate> retrieveInstanceTemplates() throws IOException {
    InstanceTemplates instanceTemplatesApi = computeApiFactory.createInstanceTemplates(credentials);
    return instanceTemplatesApi.list().execute();
  }

  Collection<String> getZonesForRegion() {
    return Optional.ofNullable(credentials.getZonesFromRegion(region)).orElse(ImmutableList.of());
  }

  String getBatchContext(String subcontext) {
    return String.join(".", getBatchContextPrefix(), subcontext);
  }

  /**
   * Get the first part of the batch context that will be passed to the {@link BatchComputeRequest
   * batch requests} used by this caching agent.
   */
  abstract String getBatchContextPrefix();

  @Override
  public String getProviderName() {
    return GoogleInfrastructureProvider.class.getName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return DATA_TYPES;
  }

  @Override
  public String getAgentType() {
    return String.format("%s/%s/%s", getAccountName(), region, getClass().getSimpleName());
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return onDemandMetricsSupport;
  }

  @Override
  public String getAccountName() {
    return credentials.getName();
  }

  GoogleNamedAccountCredentials getCredentials() {
    return credentials;
  }

  GoogleComputeApiFactory getComputeApiFactory() {
    return computeApiFactory;
  }

  String getRegion() {
    return region;
  }
}
