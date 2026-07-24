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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxLxc;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxVm;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxmoxServerClusterProviderTest {

  private static final String ACCOUNT = "myaccount";
  private static final String NODE = "pve01";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private InMemoryCache cache;
  private ProxmoxServerClusterProvider provider;

  @BeforeEach
  void setUp() {
    cache = new InMemoryCache();
    provider = new ProxmoxServerClusterProvider(cache, new ProxmoxTagNamer());
  }

  // ── getClusters() ─────────────────────────────────────────────────────────

  @Test
  void getClustersGroupsVmsByAppAndCluster() {
    // Two VMs in the same app but different clusters — result keyed by account name.
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");
    putVm(
        102,
        "myapp-staging-v001",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-staging",
        "stopped");

    Map<String, Set<ProxmoxServerCluster>> result = provider.getClusters();

    assertThat(result).containsOnlyKeys(ACCOUNT);
    Set<ProxmoxServerCluster> accountClusters = result.get(ACCOUNT);
    assertThat(accountClusters).hasSize(2);
    assertThat(
            accountClusters.stream().map(ProxmoxServerCluster::getName).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("myapp-prod", "myapp-staging");
  }

  @Test
  void getClustersWithTwoVmsSameClusterNoServerGroupTag() {
    // Without spinnaker-server-group, each VM becomes its own server group.
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");
    putVm(102, "myapp-prod-v002", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    Map<String, Set<ProxmoxServerCluster>> result = provider.getClusters();

    assertThat(result.get(ACCOUNT)).hasSize(1);
    ProxmoxServerCluster cluster = result.get(ACCOUNT).iterator().next();
    assertThat(cluster.getServerGroups()).hasSize(2);
  }

  @Test
  void getClustersReturnsEmptyWhenCacheIsEmpty() {
    assertThat(provider.getClusters()).isEmpty();
  }

  // ── getClusterSummaries() ─────────────────────────────────────────────────

  @Test
  void getClusterSummariesFiltersToRequestedApp() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");
    putVm(
        102,
        "otherapp-prod-v001",
        "spinnaker-app+otherapp;spinnaker-cluster+otherapp-prod",
        "running");

    Map<String, Set<ProxmoxServerCluster>> result = provider.getClusterSummaries("myapp");

    assertThat(result).containsOnlyKeys(ACCOUNT);
    assertThat(result.get(ACCOUNT)).hasSize(1);
    assertThat(result.get(ACCOUNT).iterator().next().getName()).isEqualTo("myapp-prod");
  }

  @Test
  void getClusterSummariesReturnsEmptyForUnknownApp() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    Map<String, Set<ProxmoxServerCluster>> result = provider.getClusterSummaries("other");

    assertThat(result).isEmpty();
  }

  // ── getClusters(app, account) ─────────────────────────────────────────────

  @Test
  void getClustersForAppAndAccountReturnsCorrectSet() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    Set<ProxmoxServerCluster> clusters = provider.getClusters("myapp", ACCOUNT);

    assertThat(clusters).hasSize(1);
    assertThat(clusters.iterator().next().getName()).isEqualTo("myapp-prod");
    assertThat(clusters.iterator().next().getAccountName()).isEqualTo(ACCOUNT);
  }

  @Test
  void getClustersForAppAndAccountReturnsEmptyForWrongAccount() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    Set<ProxmoxServerCluster> clusters = provider.getClusters("myapp", "other-account");

    assertThat(clusters).isEmpty();
  }

  // ── getCluster() ─────────────────────────────────────────────────────────

  @Test
  void getClusterReturnsCorrectCluster() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    ProxmoxServerCluster cluster = provider.getCluster("myapp", ACCOUNT, "myapp-prod");

    assertThat(cluster).isNotNull();
    assertThat(cluster.getName()).isEqualTo("myapp-prod");
  }

  @Test
  void getClusterReturnsNullForUnknownCluster() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    ProxmoxServerCluster cluster = provider.getCluster("myapp", ACCOUNT, "nonexistent");

    assertThat(cluster).isNull();
  }

  // ── getServerGroup() ─────────────────────────────────────────────────────

  @Test
  void getServerGroupReturnsCorrectServerGroup() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    var sg = provider.getServerGroup(ACCOUNT, NODE, "myapp-prod-v001", true);

    assertThat(sg).isNotNull();
    assertThat(sg.getName()).isEqualTo("myapp-prod-v001");
    assertThat(sg.getRegion()).isEqualTo(NODE);
  }

  @Test
  void getServerGroupReturnsNullForMissingServerGroup() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    var sg = provider.getServerGroup(ACCOUNT, NODE, "nonexistent-v001", true);

    assertThat(sg).isNull();
  }

  @Test
  void getServerGroupFallbackScanFindsTagNamedServerGroup() {
    // "TrueNasSCALE" is not Frigga-parseable to app=nas; the fallback account scan must find it.
    putVm(101, "TrueNasSCALE", "spinnaker-app+nas;spinnaker-cluster+truenas", "running");

    var sg = provider.getServerGroup(ACCOUNT, NODE, "TrueNasSCALE", true);

    assertThat(sg).isNotNull();
    assertThat(sg.getName()).isEqualTo("TrueNasSCALE");
  }

  // ── Frigga name fallback ──────────────────────────────────────────────────

  @Test
  void vmWithNoTagsIsGroupedViaFriggaNameParsing() {
    // No tags; name parsed by Frigga: app=myapp, cluster=myapp-prod
    putVm(101, "myapp-prod-v001", null, "running");

    Map<String, Set<ProxmoxServerCluster>> result = provider.getClusters();

    assertThat(result).containsKey(ACCOUNT);
    assertThat(result.get(ACCOUNT)).hasSize(1);
    assertThat(result.get(ACCOUNT).iterator().next().getName()).isEqualTo("myapp-prod");
  }

  // ── LXC containers ────────────────────────────────────────────────────────

  @Test
  void lxcContainersAreReturnedAlongsideVms() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");
    putLxc(200, "myapp-prod-v002", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    ProxmoxServerCluster cluster = provider.getCluster("myapp", ACCOUNT, "myapp-prod");

    assertThat(cluster).isNotNull();
    assertThat(cluster.getServerGroups()).hasSize(2);
  }

  @Test
  void lxcOnlyClusterIsDiscovered() {
    putLxc(
        200,
        "webct-staging-v001",
        "spinnaker-app+webct;spinnaker-cluster+webct-staging",
        "stopped");

    Map<String, Set<ProxmoxServerCluster>> result = provider.getClusters();

    assertThat(
            result.get(ACCOUNT).stream()
                .map(ProxmoxServerCluster::getName)
                .collect(Collectors.toSet()))
        .contains("webct-staging");
  }

  // ── cluster moniker ───────────────────────────────────────────────────────

  @Test
  void clusterMonikerReflectsTagsNotVmName() {
    // VM name "TrueNasSCALE" can't be Frigga-parsed to app=nas; moniker must come from tags
    putVm(101, "TrueNasSCALE", "spinnaker-app+nas;spinnaker-cluster+truenas", "running");

    ProxmoxServerCluster cluster = provider.getCluster("nas", ACCOUNT, "truenas");

    assertThat(cluster).isNotNull();
    assertThat(cluster.getMoniker()).isNotNull();
    assertThat(cluster.getMoniker().getApp()).isEqualTo("nas");
    assertThat(cluster.getMoniker().getCluster()).isEqualTo("truenas");
  }

  @Test
  void serverGroupApplicationMatchesTagAppForNonFriggaName() {
    putVm(101, "TrueNasSCALE", "spinnaker-app+nas;spinnaker-cluster+truenas", "running");

    ProxmoxServerGroup sg = firstServerGroup("nas");

    assertThat(sg.getApplication()).isEqualTo("nas");
    assertThat(sg.getMoniker().getApp()).isEqualTo("nas");
    assertThat(sg.getMoniker().getCluster()).isEqualTo("truenas");
  }

  // ── Cloud provider ID ─────────────────────────────────────────────────────

  @Test
  void cloudProviderIdIsProxmox() {
    assertThat(provider.getCloudProviderId()).isEqualTo("proxmox");
  }

  // ── serverGroup.application ───────────────────────────────────────────────

  @Test
  void serverGroupApplicationIsAppNameNotVmName() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    ProxmoxServerGroup sg = firstServerGroup("myapp");

    assertThat(sg.getApplication()).isEqualTo("myapp");
    assertThat(sg.getApplication()).isNotEqualTo(sg.getName());
  }

  @Test
  void serverGroupApplicationIsSetWhenDerivedFromFriggaNameParsing() {
    putVm(101, "myapp-prod-v001", null, "running");

    ProxmoxServerGroup sg = firstServerGroup("myapp");

    assertThat(sg.getApplication()).isEqualTo("myapp");
  }

  @Test
  void lxcServerGroupApplicationIsAppNameNotVmName() {
    putLxc(200, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    ProxmoxServerGroup sg = firstServerGroup("myapp");

    assertThat(sg.getApplication()).isEqualTo("myapp");
    assertThat(sg.getApplication()).isNotEqualTo(sg.getName());
  }

  // ── instance name ─────────────────────────────────────────────────────────

  @Test
  void instanceNameIsVmNameNotCompositeId() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    ProxmoxServerGroup sg = firstServerGroup("myapp");
    var instance = sg.getInstances().iterator().next();

    assertThat(instance.getName()).isEqualTo("myapp-prod-v001");
    assertThat(instance.getName()).doesNotContain("/");
  }

  @Test
  void lxcInstanceNameIsVmNameNotCompositeId() {
    putLxc(200, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    ProxmoxServerGroup sg = firstServerGroup("myapp");
    var instance = sg.getInstances().iterator().next();

    assertThat(instance.getName()).isEqualTo("myapp-prod-v001");
    assertThat(instance.getName()).doesNotContain("/");
  }

  // ── spinnaker-server-group tag: blue/green support ────────────────────────

  @Test
  void serverGroupTagGroupsMultipleVmsIntoOneServerGroup() {
    // Two VMs tagged with the same spinnaker-server-group → one server group, two instances.
    putVm(
        101,
        "myapp-prod-rev1-v001",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev1",
        "running");
    putVm(
        102,
        "myapp-prod-rev1-v002",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev1",
        "running");

    ProxmoxServerCluster cluster = provider.getCluster("myapp", ACCOUNT, "myapp-prod");

    assertThat(cluster.getServerGroups()).hasSize(1);
    ProxmoxServerGroup sg = cluster.getServerGroups().iterator().next();
    assertThat(sg.getName()).isEqualTo("myapp-prod-rev1");
    assertThat(sg.getInstances()).hasSize(2);
  }

  @Test
  void twoServerGroupTagsEnableBlueGreenDeployInSameCluster() {
    putVm(
        101,
        "myapp-prod-rev1-v001",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev1",
        "running");
    putVm(
        102,
        "myapp-prod-rev2-v001",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev2",
        "running");

    ProxmoxServerCluster cluster = provider.getCluster("myapp", ACCOUNT, "myapp-prod");

    assertThat(cluster.getServerGroups()).hasSize(2);
    assertThat(
            cluster.getServerGroups().stream()
                .map(ProxmoxServerGroup::getName)
                .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("myapp-prod-rev1", "myapp-prod-rev2");
  }

  @Test
  void vmWithoutServerGroupTagIsItsOwnServerGroup() {
    // Non-deployable VMs (no spinnaker-server-group) remain individual server groups.
    putVm(101, "TrueNasSCALE", "spinnaker-app+nas;spinnaker-cluster+truenas", "running");

    ProxmoxServerCluster cluster = provider.getCluster("nas", ACCOUNT, "truenas");

    assertThat(cluster.getServerGroups()).hasSize(1);
    ProxmoxServerGroup sg = cluster.getServerGroups().iterator().next();
    assertThat(sg.getName()).isEqualTo("TrueNasSCALE");
    assertThat(sg.getInstances()).hasSize(1);
    assertThat(sg.getInstances().iterator().next().getName()).isEqualTo("TrueNasSCALE");
  }

  @Test
  void instanceCountsAggregateAcrossMultipleVmsInServerGroup() {
    putVm(
        101,
        "myapp-prod-rev1-v001",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev1",
        "running");
    putVm(
        102,
        "myapp-prod-rev1-v002",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev1",
        "stopped");

    ProxmoxServerGroup sg =
        provider.getCluster("myapp", ACCOUNT, "myapp-prod").getServerGroups().iterator().next();

    assertThat(sg.getInstanceCounts().getTotal()).isEqualTo(2);
    assertThat(sg.getInstanceCounts().getUp()).isEqualTo(1);
    assertThat(sg.getInstanceCounts().getDown()).isEqualTo(1);
    assertThat(sg.isDisabled()).isFalse();
  }

  @Test
  void serverGroupIsDisabledWhenAllInstancesAreStopped() {
    putVm(
        101,
        "myapp-prod-rev1-v001",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev1",
        "stopped");
    putVm(
        102,
        "myapp-prod-rev1-v002",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev1",
        "stopped");

    ProxmoxServerGroup sg =
        provider.getCluster("myapp", ACCOUNT, "myapp-prod").getServerGroups().iterator().next();

    assertThat(sg.isDisabled()).isTrue();
    assertThat(sg.getInstanceCounts().getTotal()).isEqualTo(2);
    assertThat(sg.getInstanceCounts().getUp()).isEqualTo(0);
  }

  @Test
  void serverGroupNamedByTagIsLookupableByGetServerGroup() {
    putVm(
        101,
        "myapp-prod-rev1-v001",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-prod;spinnaker-server-group+myapp-prod-rev1",
        "running");

    var sg = provider.getServerGroup(ACCOUNT, NODE, "myapp-prod-rev1", true);

    assertThat(sg).isNotNull();
    assertThat(sg.getName()).isEqualTo("myapp-prod-rev1");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private ProxmoxServerGroup firstServerGroup(String app) {
    return provider.getClusters(app, ACCOUNT).iterator().next().getServerGroups().iterator().next();
  }

  private static final ProxmoxTagNamer NAMER = new ProxmoxTagNamer();

  private void putVm(int vmId, String name, String tags, String status) {
    ProxmoxVm vm =
        ProxmoxVm.builder().vmId(vmId).name(name).node(NODE).tags(tags).status(status).build();
    Map<String, Object> attrs =
        MAPPER.convertValue(vm, new TypeReference<Map<String, Object>>() {});
    String key = ProxmoxCacheKeys.vm(ACCOUNT, NODE, vmId);
    cache.merge(ProxmoxResourceType.VM.name(), new DefaultCacheData(key, attrs, Map.of()));
    mergeClusterEntry(key, ProxmoxResourceType.VM.name(), NAMER.deriveMoniker(vm));
  }

  private void putLxc(int vmId, String name, String tags, String status) {
    ProxmoxLxc lxc =
        ProxmoxLxc.builder().vmId(vmId).name(name).node(NODE).tags(tags).status(status).build();
    Map<String, Object> attrs =
        MAPPER.convertValue(lxc, new TypeReference<Map<String, Object>>() {});
    String key = ProxmoxCacheKeys.lxc(ACCOUNT, NODE, vmId);
    cache.merge(ProxmoxResourceType.CONTAINER.name(), new DefaultCacheData(key, attrs, Map.of()));
    mergeClusterEntry(key, ProxmoxResourceType.CONTAINER.name(), NAMER.deriveMoniker(lxc));
  }

  private void mergeClusterEntry(String resourceKey, String resourceType, Moniker moniker) {
    String app = moniker.getApp();
    if (app == null) return;
    String clusterName = moniker.getCluster() != null ? moniker.getCluster() : app;
    String clusterKey = ProxmoxCacheKeys.cluster(ACCOUNT, clusterName);

    // InMemoryCache.merge() replaces (not unions) relationship values for the same key, so we
    // read the existing entry and rebuild the complete relationship list before writing.
    var existing = cache.get(ProxmoxResourceType.CLUSTER.name(), clusterKey);
    List<String> vmKeys =
        existing != null
            ? new ArrayList<>(
                existing.getRelationships().getOrDefault(ProxmoxResourceType.VM.name(), List.of()))
            : new ArrayList<>();
    List<String> lxcKeys =
        existing != null
            ? new ArrayList<>(
                existing
                    .getRelationships()
                    .getOrDefault(ProxmoxResourceType.CONTAINER.name(), List.of()))
            : new ArrayList<>();

    if (ProxmoxResourceType.VM.name().equals(resourceType)) {
      vmKeys.add(resourceKey);
    } else {
      lxcKeys.add(resourceKey);
    }

    Map<String, Object> clusterAttrs =
        new HashMap<>(Map.of("name", clusterName, "accountName", ACCOUNT, "app", app));
    Map<String, Collection<String>> clusterRels = new HashMap<>();
    if (!vmKeys.isEmpty()) clusterRels.put(ProxmoxResourceType.VM.name(), vmKeys);
    if (!lxcKeys.isEmpty()) clusterRels.put(ProxmoxResourceType.CONTAINER.name(), lxcKeys);
    cache.merge(
        ProxmoxResourceType.CLUSTER.name(),
        new DefaultCacheData(clusterKey, clusterAttrs, clusterRels));
  }
}
