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

  @Mock private ProxmoxServerClusterProvider clusterProvider;

  private ProxmoxApplicationProvider applicationProvider;

  @BeforeEach
  void setUp() {
    applicationProvider = new ProxmoxApplicationProvider(clusterProvider);
  }

  // ── getApplications(true) — with cluster expansion ────────────────────────

  @Test
  void getApplicationsExpandedReturnsAppsWithClusterNames() {
    ProxmoxServerCluster cluster = buildCluster("myapp-prod", ACCOUNT);
    when(clusterProvider.getClusters()).thenReturn(Map.of("myapp", Set.of(cluster)));

    Set<ProxmoxApplication> apps = applicationProvider.getApplications(true);

    assertThat(apps).hasSize(1);
    ProxmoxApplication app = apps.iterator().next();
    assertThat(app.getName()).isEqualTo("myapp");
    assertThat(app.getClusterNames()).containsKey(ACCOUNT);
    assertThat(app.getClusterNames().get(ACCOUNT)).contains("myapp-prod");
  }

  @Test
  void getApplicationsExpandedWithTwoClustersInSameAccount() {
    ProxmoxServerCluster cluster1 = buildCluster("myapp-prod", ACCOUNT);
    ProxmoxServerCluster cluster2 = buildCluster("myapp-staging", ACCOUNT);
    when(clusterProvider.getClusters()).thenReturn(Map.of("myapp", Set.of(cluster1, cluster2)));

    Set<ProxmoxApplication> apps = applicationProvider.getApplications(true);

    assertThat(apps).hasSize(1);
    ProxmoxApplication app = apps.iterator().next();
    assertThat(app.getClusterNames().get(ACCOUNT))
        .containsExactlyInAnyOrder("myapp-prod", "myapp-staging");
  }

  @Test
  void getApplicationsExpandedWithTwoApps() {
    ProxmoxServerCluster c1 = buildCluster("myapp-prod", ACCOUNT);
    ProxmoxServerCluster c2 = buildCluster("otherapp-prod", ACCOUNT);
    when(clusterProvider.getClusters())
        .thenReturn(Map.of("myapp", Set.of(c1), "otherapp", Set.of(c2)));

    Set<ProxmoxApplication> apps = applicationProvider.getApplications(true);

    assertThat(apps).hasSize(2);
    Set<String> appNames =
        apps.stream().map(ProxmoxApplication::getName).collect(java.util.stream.Collectors.toSet());
    assertThat(appNames).containsExactlyInAnyOrder("myapp", "otherapp");
  }

  // ── getApplications(false) — without expansion ───────────────────────────

  @Test
  void getApplicationsNotExpandedReturnsEmptyClusterNames() {
    ProxmoxServerCluster cluster = buildCluster("myapp-prod", ACCOUNT);
    when(clusterProvider.getClusters()).thenReturn(Map.of("myapp", Set.of(cluster)));

    Set<ProxmoxApplication> apps = applicationProvider.getApplications(false);

    assertThat(apps).hasSize(1);
    ProxmoxApplication app = apps.iterator().next();
    assertThat(app.getName()).isEqualTo("myapp");
    assertThat(app.getClusterNames()).isEmpty();
  }

  // ── getApplication(name) ─────────────────────────────────────────────────

  @Test
  void getApplicationReturnsCorrectApp() {
    ProxmoxServerCluster cluster = buildCluster("myapp-prod", ACCOUNT);
    when(clusterProvider.getClusterDetails("myapp")).thenReturn(Map.of("myapp", Set.of(cluster)));

    ProxmoxApplication app = applicationProvider.getApplication("myapp");

    assertThat(app).isNotNull();
    assertThat(app.getName()).isEqualTo("myapp");
    assertThat(app.getClusterNames()).containsKey(ACCOUNT);
  }

  @Test
  void getApplicationReturnsNullForNonexistentApp() {
    when(clusterProvider.getClusterDetails("nonexistent")).thenReturn(Map.of());

    ProxmoxApplication app = applicationProvider.getApplication("nonexistent");

    assertThat(app).isNull();
  }

  @Test
  void getApplicationAttributesContainName() {
    ProxmoxServerCluster cluster = buildCluster("myapp-prod", ACCOUNT);
    when(clusterProvider.getClusterDetails("myapp")).thenReturn(Map.of("myapp", Set.of(cluster)));

    ProxmoxApplication app = applicationProvider.getApplication("myapp");

    assertThat(app.getAttributes()).containsEntry("name", "myapp");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static ProxmoxServerCluster buildCluster(String name, String account) {
    ProxmoxServerCluster cluster = new ProxmoxServerCluster();
    cluster.setName(name);
    cluster.setAccountName(account);
    return cluster;
  }
}
