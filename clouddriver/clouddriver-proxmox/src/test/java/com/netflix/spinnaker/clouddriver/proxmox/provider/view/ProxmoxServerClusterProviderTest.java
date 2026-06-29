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
import java.util.Map;
import java.util.Set;
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
    // Two VMs in the same app but different clusters
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");
    putVm(
        102,
        "myapp-staging-v001",
        "spinnaker-app+myapp;spinnaker-cluster+myapp-staging",
        "stopped");

    Map<String, Set<ProxmoxServerCluster>> clusters = provider.getClusters();

    assertThat(clusters).containsKey("myapp");
    Set<ProxmoxServerCluster> appClusters = clusters.get("myapp");
    assertThat(appClusters).hasSize(2);

    Set<String> clusterNames =
        appClusters.stream()
            .map(ProxmoxServerCluster::getName)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(clusterNames).containsExactlyInAnyOrder("myapp-prod", "myapp-staging");
  }

  @Test
  void getClustersWithTwoVmsSameCluster() {
    putVm(101, "myapp-prod-v001", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");
    putVm(102, "myapp-prod-v002", "spinnaker-app+myapp;spinnaker-cluster+myapp-prod", "running");

    Map<String, Set<ProxmoxServerCluster>> clusters = provider.getClusters();

    assertThat(clusters.get("myapp")).hasSize(1);
    ProxmoxServerCluster cluster = clusters.get("myapp").iterator().next();
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

    assertThat(result).containsOnlyKeys("myapp");
    assertThat(result.get("myapp")).hasSize(1);
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

  // ── Frigga name fallback ──────────────────────────────────────────────────

  @Test
  void vmWithNoTagsIsGroupedViaFriggaNameParsing() {
    // No tags; name parsed by Frigga: app=myapp, cluster=myapp-prod
    putVm(101, "myapp-prod-v001", null, "running");

    Map<String, Set<ProxmoxServerCluster>> clusters = provider.getClusters();

    assertThat(clusters).containsKey("myapp");
    assertThat(clusters.get("myapp")).hasSize(1);
    assertThat(clusters.get("myapp").iterator().next().getName()).isEqualTo("myapp-prod");
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

    Map<String, Set<ProxmoxServerCluster>> clusters = provider.getClusters();

    assertThat(clusters).containsKey("webct");
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

  // ── helpers ───────────────────────────────────────────────────────────────

  private ProxmoxServerGroup firstServerGroup(String app) {
    return provider.getClusters().get(app).iterator().next().getServerGroups().iterator().next();
  }

  private void putVm(int vmId, String name, String tags, String status) {
    ProxmoxVm vm =
        ProxmoxVm.builder().vmId(vmId).name(name).node(NODE).tags(tags).status(status).build();
    Map<String, Object> attrs =
        MAPPER.convertValue(vm, new TypeReference<Map<String, Object>>() {});
    String key = ProxmoxCacheKeys.vm(ACCOUNT, NODE, vmId);
    cache.merge(ProxmoxResourceType.VM.name(), new DefaultCacheData(key, attrs, Map.of()));
  }

  private void putLxc(int vmId, String name, String tags, String status) {
    ProxmoxLxc lxc =
        ProxmoxLxc.builder().vmId(vmId).name(name).node(NODE).tags(tags).status(status).build();
    Map<String, Object> attrs =
        MAPPER.convertValue(lxc, new TypeReference<Map<String, Object>>() {});
    String key = ProxmoxCacheKeys.lxc(ACCOUNT, NODE, vmId);
    cache.merge(ProxmoxResourceType.CONTAINER.name(), new DefaultCacheData(key, attrs, Map.of()));
  }
}
