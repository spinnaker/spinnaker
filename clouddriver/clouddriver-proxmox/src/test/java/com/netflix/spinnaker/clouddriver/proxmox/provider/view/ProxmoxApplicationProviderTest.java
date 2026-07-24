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
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxmoxApplicationProviderTest {

  private static final String ACCOUNT = "myaccount";
  private static final String APP_TYPE = ProxmoxResourceType.APPLICATION.name();

  @Mock private Cache cacheView;
  @Mock private ProxmoxServerClusterProvider clusterProvider;

  private ProxmoxApplicationProvider applicationProvider;

  @BeforeEach
  void setUp() {
    applicationProvider = new ProxmoxApplicationProvider(cacheView, clusterProvider);
  }

  // ── getApplications(true) — with cluster expansion ────────────────────────

  @Test
  void getApplicationsExpandedReturnsAppsWithClusterNames() {
    stubAppCache("myapp");
    ProxmoxServerCluster cluster = buildCluster("myapp-prod", ACCOUNT);
    when(clusterProvider.getClusterDetails("myapp")).thenReturn(Map.of(ACCOUNT, Set.of(cluster)));

    Set<ProxmoxApplication> apps = applicationProvider.getApplications(true);

    assertThat(apps).hasSize(1);
    ProxmoxApplication app = apps.iterator().next();
    assertThat(app.getName()).isEqualTo("myapp");
    assertThat(app.getClusterNames()).containsKey(ACCOUNT);
    assertThat(app.getClusterNames().get(ACCOUNT)).contains("myapp-prod");
  }

  @Test
  void getApplicationsExpandedWithTwoClustersInSameAccount() {
    stubAppCache("myapp");
    ProxmoxServerCluster cluster1 = buildCluster("myapp-prod", ACCOUNT);
    ProxmoxServerCluster cluster2 = buildCluster("myapp-staging", ACCOUNT);
    when(clusterProvider.getClusterDetails("myapp"))
        .thenReturn(Map.of(ACCOUNT, Set.of(cluster1, cluster2)));

    Set<ProxmoxApplication> apps = applicationProvider.getApplications(true);

    assertThat(apps).hasSize(1);
    ProxmoxApplication app = apps.iterator().next();
    assertThat(app.getClusterNames().get(ACCOUNT))
        .containsExactlyInAnyOrder("myapp-prod", "myapp-staging");
  }

  @Test
  void getApplicationsExpandedWithTwoApps() {
    stubAppCache("myapp", "otherapp");
    ProxmoxServerCluster c1 = buildCluster("myapp-prod", ACCOUNT);
    ProxmoxServerCluster c2 = buildCluster("otherapp-prod", ACCOUNT);
    when(clusterProvider.getClusterDetails("myapp")).thenReturn(Map.of(ACCOUNT, Set.of(c1)));
    when(clusterProvider.getClusterDetails("otherapp")).thenReturn(Map.of(ACCOUNT, Set.of(c2)));

    Set<ProxmoxApplication> apps = applicationProvider.getApplications(true);

    assertThat(apps).hasSize(2);
    Set<String> appNames =
        apps.stream().map(ProxmoxApplication::getName).collect(java.util.stream.Collectors.toSet());
    assertThat(appNames).containsExactlyInAnyOrder("myapp", "otherapp");
  }

  // ── getApplications(false) — without expansion ───────────────────────────

  @Test
  void getApplicationsNotExpandedReturnsEmptyClusterNames() {
    stubAppCache("myapp");

    Set<ProxmoxApplication> apps = applicationProvider.getApplications(false);

    assertThat(apps).hasSize(1);
    ProxmoxApplication app = apps.iterator().next();
    assertThat(app.getName()).isEqualTo("myapp");
    assertThat(app.getClusterNames()).isEmpty();
  }

  // ── getApplication(name) ─────────────────────────────────────────────────

  @Test
  void getApplicationReturnsCorrectApp() {
    stubSingleAppCache("myapp");
    ProxmoxServerCluster cluster = buildCluster("myapp-prod", ACCOUNT);
    when(clusterProvider.getClusterDetails("myapp")).thenReturn(Map.of(ACCOUNT, Set.of(cluster)));

    ProxmoxApplication app = applicationProvider.getApplication("myapp");

    assertThat(app).isNotNull();
    assertThat(app.getName()).isEqualTo("myapp");
    assertThat(app.getClusterNames()).containsKey(ACCOUNT);
  }

  @Test
  void getApplicationReturnsNullForNonexistentApp() {
    when(cacheView.get(APP_TYPE, ProxmoxCacheKeys.application("nonexistent"))).thenReturn(null);

    ProxmoxApplication app = applicationProvider.getApplication("nonexistent");

    assertThat(app).isNull();
  }

  @Test
  void getApplicationAttributesContainName() {
    stubSingleAppCache("myapp");
    ProxmoxServerCluster cluster = buildCluster("myapp-prod", ACCOUNT);
    when(clusterProvider.getClusterDetails("myapp")).thenReturn(Map.of(ACCOUNT, Set.of(cluster)));

    ProxmoxApplication app = applicationProvider.getApplication("myapp");

    assertThat(app.getAttributes()).containsEntry("name", "myapp");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void stubAppCache(String... appNames) {
    Collection<String> keys = new java.util.ArrayList<>();
    Collection<com.netflix.spinnaker.cats.cache.CacheData> dataList = new java.util.ArrayList<>();
    for (String name : appNames) {
      String key = ProxmoxCacheKeys.application(name);
      keys.add(key);
      dataList.add(new DefaultCacheData(key, Map.of("name", name), Map.of()));
    }
    when(cacheView.filterIdentifiers(APP_TYPE, ProxmoxCacheKeys.globAllApplications()))
        .thenReturn(keys);
    when(cacheView.getAll(APP_TYPE, keys)).thenReturn(dataList);
  }

  private void stubSingleAppCache(String name) {
    String key = ProxmoxCacheKeys.application(name);
    when(cacheView.get(APP_TYPE, key))
        .thenReturn(new DefaultCacheData(key, Map.of("name", name), Map.of()));
  }

  private static ProxmoxServerCluster buildCluster(String name, String account) {
    ProxmoxServerCluster cluster = new ProxmoxServerCluster();
    cluster.setName(name);
    cluster.setAccountName(account);
    return cluster;
  }
}
