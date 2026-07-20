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
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxLxc;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxResource;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    return all.getOrDefault(account, Collections.emptySet());
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
    // Fast path: derive app from the server group name via Frigga (works for Frigga-named SGs)
    Moniker moniker = tagNamer.deriveMoniker(namedResource(serverGroupName));
    String app = moniker.getApp();
    if (app != null) {
      ServerGroup found =
          getClusters(app, account).stream()
              .flatMap(c -> c.getServerGroups().stream())
              .filter(sg -> serverGroupName.equals(sg.getName()))
              .findFirst()
              .orElse(null);
      if (found != null) return found;
    }

    // Fallback: full scan across all apps in this account.
    // Needed for tag-named server groups whose names aren't Frigga-parseable (e.g. "TrueNasSCALE").
    return buildClusterMap(null, account).values().stream()
        .flatMap(Set::stream)
        .flatMap(c -> c.getServerGroups().stream())
        .filter(sg -> serverGroupName.equals(sg.getName()))
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

    String clusterGlob =
        accountFilter != null
            ? ProxmoxCacheKeys.glob(ProxmoxResourceType.CLUSTER.name(), accountFilter)
            : ProxmoxCacheKeys.globAll(ProxmoxResourceType.CLUSTER.name());

    Collection<String> clusterIds =
        cacheView.filterIdentifiers(ProxmoxResourceType.CLUSTER.name(), clusterGlob);
    if (clusterIds.isEmpty()) return result;

    Collection<CacheData> clusterItems =
        cacheView.getAll(ProxmoxResourceType.CLUSTER.name(), clusterIds);

    for (CacheData clusterEntry : clusterItems) {
      String clusterName = (String) clusterEntry.getAttributes().get("name");
      String account = (String) clusterEntry.getAttributes().get("accountName");
      String app = (String) clusterEntry.getAttributes().get("app");

      if (clusterName == null || account == null || app == null) continue;
      if (applicationFilter != null && !applicationFilter.equals(app)) continue;

      ProxmoxServerCluster cluster = new ProxmoxServerCluster();
      cluster.setName(clusterName);
      cluster.setAccountName(account);
      cluster.setMoniker(Moniker.builder().app(app).cluster(clusterName).build());

      Collection<String> vmRelIds =
          clusterEntry.getRelationships().getOrDefault(ProxmoxResourceType.VM.name(), List.of());
      Collection<String> lxcRelIds =
          clusterEntry
              .getRelationships()
              .getOrDefault(ProxmoxResourceType.CONTAINER.name(), List.of());

      if (!vmRelIds.isEmpty()) {
        for (CacheData item :
            cacheView.getAll(ProxmoxResourceType.VM.name(), new ArrayList<>(vmRelIds))) {
          addToCluster(item, ProxmoxVm.class, cluster, app, clusterName);
        }
      }
      if (!lxcRelIds.isEmpty()) {
        for (CacheData item :
            cacheView.getAll(ProxmoxResourceType.CONTAINER.name(), new ArrayList<>(lxcRelIds))) {
          addToCluster(item, ProxmoxLxc.class, cluster, app, clusterName);
        }
      }

      result.computeIfAbsent(account, k -> new HashSet<>()).add(cluster);
    }

    return result;
  }

  private <T extends ProxmoxResource> void addToCluster(
      CacheData item,
      Class<T> resourceClass,
      ProxmoxServerCluster cluster,
      String app,
      String clusterName) {

    T resource;
    try {
      resource = objectMapper.convertValue(item.getAttributes(), resourceClass);
    } catch (Exception e) {
      log.warn(
          "Failed to deserialize cache item {} as {}", item.getId(), resourceClass.getSimpleName());
      return;
    }

    String node = ProxmoxCacheKeys.getNode(item.getId());
    if (node == null) return;

    Map<String, String> tagMap = ProxmoxTagNamer.parseTags(resource.getTags());
    String serverGroupName =
        tagMap.getOrDefault(ProxmoxTagNamer.SERVER_GROUP_TAG, resource.getName());

    ProxmoxInstance instance = buildInstance(resource, node);

    final String finalSgName = serverGroupName;
    ProxmoxServerGroup sg =
        cluster.getServerGroups().stream()
            .filter(s -> finalSgName.equals(s.getName()))
            .findFirst()
            .orElseGet(
                () -> {
                  ProxmoxServerGroup s = new ProxmoxServerGroup();
                  s.setName(finalSgName);
                  s.setApplication(app);
                  s.setRegion(node);
                  s.setZones(new HashSet<>(Set.of(node)));
                  s.setInstances(new HashSet<>());
                  s.setLoadBalancers(Collections.emptySet());
                  s.setSecurityGroups(Collections.emptySet());
                  s.setLaunchConfig(buildLaunchConfig(resource));
                  // Carry the full tag-derived moniker (stack/detail/sequence) so deck can order
                  // server group versions within the cluster.
                  Moniker derived = tagNamer.deriveMoniker(resource);
                  derived.setApp(app);
                  derived.setCluster(clusterName);
                  s.setMoniker(derived);
                  cluster.getServerGroups().add(s);
                  return s;
                });

    sg.getInstances().add(instance);
    sg.getZones().add(node);
    recomputeCounts(sg);
  }

  private Map<String, Object> buildLaunchConfig(ProxmoxResource resource) {
    Map<String, Object> config = new HashMap<>();
    if (resource instanceof ProxmoxVm vm) {
      if (vm.getCpus() != null) config.put("cpus", vm.getCpus());
      if (vm.getCores() != null) config.put("cores", vm.getCores());
      if (vm.getSockets() != null) config.put("sockets", vm.getSockets());
      if (vm.getMaxMem() != null) config.put("memoryMb", vm.getMaxMem() / (1024 * 1024));
      if (vm.getMaxDisk() != null) config.put("diskGb", vm.getMaxDisk() / (1024 * 1024 * 1024));
      if (vm.getOsType() != null) config.put("osType", vm.getOsType());
      if (vm.getMachine() != null) config.put("machine", vm.getMachine());
      if (vm.getBios() != null) config.put("bios", vm.getBios());
      if (vm.getStatus() != null) config.put("status", vm.getStatus());
      if (vm.getVmId() != null) config.put("vmId", vm.getVmId());
      if (vm.getDisks() != null && !vm.getDisks().isEmpty()) config.put("disks", vm.getDisks());
    } else if (resource instanceof ProxmoxLxc lxc) {
      if (lxc.getCpus() != null) config.put("cpus", lxc.getCpus());
      if (lxc.getMaxMem() != null) config.put("memoryMb", lxc.getMaxMem() / (1024 * 1024));
      if (lxc.getMaxDisk() != null) config.put("diskGb", lxc.getMaxDisk() / (1024 * 1024 * 1024));
      if (lxc.getOsType() != null) config.put("osType", lxc.getOsType());
      if (lxc.getStatus() != null) config.put("status", lxc.getStatus());
      if (lxc.getVmId() != null) config.put("vmId", lxc.getVmId());
      if (lxc.getDisks() != null && !lxc.getDisks().isEmpty()) config.put("disks", lxc.getDisks());
    }
    return config;
  }

  private ProxmoxInstance buildInstance(ProxmoxResource resource, String node) {
    String status = statusOf(resource);
    HealthState healthState = ProxmoxInstance.healthStateFrom(status);

    Map<String, Object> healthEntry = new HashMap<>();
    healthEntry.put("type", "Proxmox");
    healthEntry.put("status", healthState.name());
    healthEntry.put("state", status != null ? status : "unknown");

    ProxmoxInstance.ProxmoxInstanceBuilder builder =
        ProxmoxInstance.builder()
            .name(resource.getName())
            .zone(node)
            .healthState(healthState)
            .health(List.of(healthEntry))
            .vmId(resource.getVmId())
            .status(status)
            .tags(ProxmoxTagNamer.parseTags(resource.getTags()));

    if (resource instanceof ProxmoxVm vm) {
      builder.vmType("qemu");
      if (vm.getCpus() != null) builder.cpus(vm.getCpus());
      if (vm.getMaxMem() != null) builder.memoryMb(vm.getMaxMem() / (1024 * 1024));
      if (vm.getMaxDisk() != null) builder.diskGb(vm.getMaxDisk() / (1024 * 1024 * 1024));
      if (vm.getOsType() != null) builder.osType(vm.getOsType());
      applyRuntimeStats(
          builder,
          vm.getUptime(),
          status,
          vm.getCpu(),
          vm.getMem(),
          vm.getDisk(),
          vm.getNetIn(),
          vm.getNetOut(),
          vm.getDiskRead(),
          vm.getDiskWrite());
      builder
          .sockets(vm.getSockets())
          .cores(vm.getCores())
          .machine(vm.getMachine())
          .bios(vm.getBios())
          .bootOrder(vm.getBoot())
          .scsiController(vm.getScsiHw())
          .net0(vm.getNet0())
          .disk0(vm.getScsi0())
          .disks(vm.getDisks())
          .description(vm.getDescription())
          .qmpStatus(vm.getQmpStatus());
      if (vm.getAgent() != null) builder.agentEnabled(vm.getAgent().startsWith("1"));
      if (vm.getOnBoot() != null) builder.onBoot(vm.getOnBoot() == 1);
      if (vm.getProtection() != null) builder.protection(vm.getProtection() == 1);
      if (vm.getHa() != null && vm.getHa().getManaged() != null) {
        builder.haManaged(vm.getHa().getManaged() == 1);
      }
    } else if (resource instanceof ProxmoxLxc lxc) {
      builder.vmType("lxc");
      if (lxc.getCpus() != null) builder.cpus(lxc.getCpus());
      if (lxc.getMaxMem() != null) builder.memoryMb(lxc.getMaxMem() / (1024 * 1024));
      if (lxc.getMaxDisk() != null) builder.diskGb(lxc.getMaxDisk() / (1024 * 1024 * 1024));
      if (lxc.getOsType() != null) builder.osType(lxc.getOsType());
      applyRuntimeStats(
          builder,
          lxc.getUptime(),
          status,
          lxc.getCpu(),
          lxc.getMem(),
          lxc.getDisk(),
          lxc.getNetIn(),
          lxc.getNetOut(),
          lxc.getDiskRead(),
          lxc.getDiskWrite());
      builder
          .net0(lxc.getNet0())
          .disk0(lxc.getRootFs())
          .disks(lxc.getDisks())
          .description(lxc.getDescription());
      if (lxc.getSwap() != null) builder.swapUsedMb(lxc.getSwap() / (1024 * 1024));
      if (lxc.getMaxSwap() != null) builder.swapMb(lxc.getMaxSwap() / (1024 * 1024));
      if (lxc.getOnBoot() != null) builder.onBoot(lxc.getOnBoot() == 1);
      if (lxc.getProtection() != null) builder.protection(lxc.getProtection() == 1);
    }

    return builder.build();
  }

  private void applyRuntimeStats(
      ProxmoxInstance.ProxmoxInstanceBuilder builder,
      Long uptime,
      String status,
      Double cpu,
      Long mem,
      Long disk,
      Long netIn,
      Long netOut,
      Long diskRead,
      Long diskWrite) {
    if (uptime != null) {
      builder.uptimeSeconds(uptime);
      if (uptime > 0 && "running".equals(status)) {
        builder.launchTime(System.currentTimeMillis() - uptime * 1000);
      }
    }
    if (cpu != null) builder.cpuUsage(cpu);
    if (mem != null) builder.memoryUsedMb(mem / (1024 * 1024));
    if (disk != null) builder.diskUsedGb(disk / (1024 * 1024 * 1024));
    builder
        .networkInBytes(netIn)
        .networkOutBytes(netOut)
        .diskReadBytes(diskRead)
        .diskWriteBytes(diskWrite);
  }

  private void recomputeCounts(ProxmoxServerGroup sg) {
    int total = sg.getInstances().size();
    int up =
        (int) sg.getInstances().stream().filter(i -> i.getHealthState() == HealthState.Up).count();
    int down = total - up;

    ServerGroup.InstanceCounts counts = new ServerGroup.InstanceCounts();
    counts.setTotal(total);
    counts.setUp(up);
    counts.setDown(down);
    sg.setInstanceCounts(counts);

    ServerGroup.Capacity capacity = new ServerGroup.Capacity();
    capacity.setMin(total);
    capacity.setMax(total);
    capacity.setDesired(total);
    sg.setCapacity(capacity);

    sg.setDisabled(up == 0);
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
