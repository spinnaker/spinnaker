/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.proxmox.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxLxc;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxResource;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProxmoxServerClusterProvider implements ClusterProvider<ProxmoxServerCluster> {

  private final Cache cacheView;
  private final ProxmoxTagNamer tagNamer;
  private final ObjectMapper objectMapper;
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public ProxmoxServerClusterProvider(Cache cacheView, ProxmoxTagNamer tagNamer) {
    this.cacheView = cacheView;
    this.tagNamer = tagNamer;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public Map<String, Set<ProxmoxServerCluster>> getClusters() {
    return buildClusterMap(null, null);
  }

  @Override
  public Map<String, Set<ProxmoxServerCluster>> getClusterSummaries(String application) {
    return buildClusterMap(application, null);
  }

  @Override
  public Map<String, Set<ProxmoxServerCluster>> getClusterDetails(String application) {
    return buildClusterMap(application, null);
  }

  @Override
  public Set<ProxmoxServerCluster> getClusters(String application, String account) {
    Map<String, Set<ProxmoxServerCluster>> all = buildClusterMap(application, account);
    return all.getOrDefault(application, Collections.emptySet());
  }

  @Override
  public ProxmoxServerCluster getCluster(String application, String account, String name) {
    return getClusters(application, account).stream()
        .filter(c -> name.equals(c.getName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public ProxmoxServerCluster getCluster(
      String application, String account, String name, boolean includeDetails) {
    return getCluster(application, account, name);
  }

  @Override
  public ServerGroup getServerGroup(
      String account, String region, String serverGroupName, boolean includeDetails) {
    Moniker moniker = tagNamer.deriveMoniker(namedResource(serverGroupName));
    String app = moniker.getApp();
    if (app == null) return null;

    return getClusters(app, account).stream()
        .flatMap(c -> c.getServerGroups().stream())
        .filter(sg -> region.equals(sg.getRegion()) && serverGroupName.equals(sg.getName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public ServerGroup getServerGroup(String account, String region, String serverGroupName) {
    return getServerGroup(account, region, serverGroupName, true);
  }

  @Override
  public String getCloudProviderId() {
    return ProxmoxProvider.ID;
  }

  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

  private Map<String, Set<ProxmoxServerCluster>> buildClusterMap(
      String applicationFilter, String accountFilter) {
    Map<String, Set<ProxmoxServerCluster>> result = new HashMap<>();

    String vmGlob =
        accountFilter != null
            ? ProxmoxCacheKeys.glob(ProxmoxResourceType.VM.name(), accountFilter)
            : ProxmoxCacheKeys.globAll(ProxmoxResourceType.VM.name());
    String lxcGlob =
        accountFilter != null
            ? ProxmoxCacheKeys.glob(ProxmoxResourceType.CONTAINER.name(), accountFilter)
            : ProxmoxCacheKeys.globAll(ProxmoxResourceType.CONTAINER.name());

    Collection<String> vmIds = cacheView.filterIdentifiers(ProxmoxResourceType.VM.name(), vmGlob);
    Collection<String> lxcIds =
        cacheView.filterIdentifiers(ProxmoxResourceType.CONTAINER.name(), lxcGlob);

    Collection<CacheData> vms = cacheView.getAll(ProxmoxResourceType.VM.name(), vmIds);
    Collection<CacheData> lxcs = cacheView.getAll(ProxmoxResourceType.CONTAINER.name(), lxcIds);

    for (CacheData item : vms) {
      processItem(item, ProxmoxVm.class, applicationFilter, result);
    }
    for (CacheData item : lxcs) {
      processItem(item, ProxmoxLxc.class, applicationFilter, result);
    }

    return result;
  }

  private <T extends ProxmoxResource> void processItem(
      CacheData item,
      Class<T> resourceClass,
      String applicationFilter,
      Map<String, Set<ProxmoxServerCluster>> result) {

    T resource;
    try {
      resource = objectMapper.convertValue(item.getAttributes(), resourceClass);
    } catch (Exception e) {
      log.warn(
          "Failed to deserialize cache item {} as {}", item.getId(), resourceClass.getSimpleName());
      return;
    }

    Moniker moniker = tagNamer.deriveMoniker(resource);
    String app = moniker.getApp();
    if (app == null) return;
    if (applicationFilter != null && !app.equals(applicationFilter)) return;

    String clusterName = moniker.getCluster() != null ? moniker.getCluster() : app;
    String account = ProxmoxCacheKeys.getAccount(item.getId());
    String node = ProxmoxCacheKeys.getNode(item.getId());
    if (account == null || node == null) return;

    ProxmoxInstance instance = buildInstance(resource, account, node);
    ProxmoxServerGroup serverGroup = buildServerGroup(resource, account, node, moniker, instance);

    result.computeIfAbsent(app, k -> new HashSet<>());
    Set<ProxmoxServerCluster> clusters = result.get(app);

    String finalClusterName = clusterName;
    Optional<ProxmoxServerCluster> existing =
        clusters.stream()
            .filter(c -> finalClusterName.equals(c.getName()) && account.equals(c.getAccountName()))
            .findFirst();

    if (existing.isPresent()) {
      existing.get().getServerGroups().add(serverGroup);
    } else {
      ProxmoxServerCluster cluster = new ProxmoxServerCluster();
      cluster.setName(clusterName);
      cluster.setAccountName(account);
      cluster.getServerGroups().add(serverGroup);
      clusters.add(cluster);
    }
  }

  private ProxmoxInstance buildInstance(ProxmoxResource resource, String account, String node) {
    String status = statusOf(resource);
    HealthState healthState = ProxmoxInstance.healthStateFrom(status);
    String instanceName = resource.getName();

    Map<String, Object> healthEntry = new HashMap<>();
    healthEntry.put("type", "Proxmox");
    healthEntry.put("status", healthState.name());
    healthEntry.put("state", status != null ? status : "unknown");

    return ProxmoxInstance.builder()
        .name(instanceName)
        .zone(node)
        .healthState(healthState)
        .health(List.of(healthEntry))
        .build();
  }

  private ProxmoxServerGroup buildServerGroup(
      ProxmoxResource resource,
      String account,
      String node,
      Moniker moniker,
      ProxmoxInstance instance) {
    String status = statusOf(resource);
    boolean running = "running".equals(status);

    ServerGroup.InstanceCounts counts = new ServerGroup.InstanceCounts();
    counts.setTotal(1);
    if (running) counts.setUp(1);
    else counts.setDown(1);

    ServerGroup.Capacity capacity = new ServerGroup.Capacity();
    capacity.setMin(1);
    capacity.setMax(1);
    capacity.setDesired(1);

    ProxmoxServerGroup sg = new ProxmoxServerGroup();
    sg.setName(resource.getName());
    sg.setApplication(moniker.getApp());
    sg.setRegion(node);
    sg.setDisabled(!running);
    sg.setZones(Set.of(node));
    sg.setInstances(new HashSet<Instance>(Set.of(instance)));
    sg.setLoadBalancers(Collections.emptySet());
    sg.setSecurityGroups(Collections.emptySet());
    sg.setLaunchConfig(Collections.emptyMap());
    sg.setInstanceCounts(counts);
    sg.setCapacity(capacity);
    sg.setMoniker(moniker);
    return sg;
  }

  private static String statusOf(ProxmoxResource resource) {
    if (resource instanceof ProxmoxVm vm) return vm.getStatus();
    if (resource instanceof ProxmoxLxc lxc) return lxc.getStatus();
    return null;
  }

  /** Minimal ProxmoxResource wrapping just a name, used for server-group lookups by name. */
  private static ProxmoxResource namedResource(String name) {
    return new ProxmoxResource() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getNode() {
        return null;
      }

      @Override
      public String getTags() {
        return null;
      }

      @Override
      public Integer getVmId() {
        return null;
      }
    };
  }
}
