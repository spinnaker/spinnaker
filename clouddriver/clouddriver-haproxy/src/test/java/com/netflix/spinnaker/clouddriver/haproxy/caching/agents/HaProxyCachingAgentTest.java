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
package com.netflix.spinnaker.clouddriver.haproxy.caching.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyCacheKeys;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyResourceType;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyNamedAccountCredentials;
import com.netflix.spinnaker.config.HaProxyConfigurationProperties;
import java.util.Collection;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HaProxyCachingAgentTest {

  private MockWebServer server;
  private HaProxyNamedAccountCredentials credentials;
  private final HaProxyMetadataNamer namer = new HaProxyMetadataNamer();
  private final DefaultRegistry registry = new DefaultRegistry();

  @BeforeEach
  void setup() throws Exception {
    server = new MockWebServer();
    server.start();

    HaProxyConfigurationProperties.HaProxyManagedAccount account =
        new HaProxyConfigurationProperties.HaProxyManagedAccount();
    account.setName("homelab");
    account.setServer(server.getHostName());
    account.setPort(server.getPort());
    account.setScheme("http");
    account.setUserName("admin");
    account.setPassword("secret");
    account.setRegion("dc1");
    credentials = new HaProxyNamedAccountCredentials(account);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private static CacheData entry(Collection<CacheData> data, String id) {
    return data.stream().filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void frontendAgentCachesFrontendsWithApplicationAndClusterRelationships() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "[{\"name\":\"web-main\",\"mode\":\"http\",\"default_backend\":\"web-main-v001\","
                    + "\"metadata\":{\"spinnaker-app\":\"web\",\"spinnaker-stack\":\"main\"},"
                    + "\"binds\":{\"public\":{\"name\":\"public\",\"address\":\"*\",\"port\":443}}},"
                    + "{\"name\":\"unmanaged_frontend\",\"mode\":\"tcp\"}]"));

    FrontendCachingAgent agent = new FrontendCachingAgent(credentials, registry, namer);
    CacheResult result = agent.loadData(null);

    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v3/services/haproxy/configuration/frontends?full_section=true");

    String frontendKey = HaProxyCacheKeys.frontend("homelab", "dc1", "web-main");
    Collection<CacheData> frontends =
        result.getCacheResults().get(HaProxyResourceType.FRONTEND.name());
    assertThat(frontends).hasSize(2);

    CacheData frontend = entry(frontends, frontendKey);
    assertThat(frontend.getAttributes())
        .containsEntry("name", "web-main")
        .containsEntry("default_backend", "web-main-v001")
        .containsEntry("spinnakerApp", "web");
    assertThat(frontend.getAttributes().get("binds")).isNotNull();

    String appKey = HaProxyCacheKeys.application("web");
    String clusterKey = HaProxyCacheKeys.cluster("homelab", "web-main");
    assertThat(frontend.getRelationships().get(HaProxyResourceType.APPLICATION.name()))
        .containsExactly(appKey);
    assertThat(frontend.getRelationships().get(HaProxyResourceType.CLUSTER.name()))
        .containsExactly(clusterKey);

    CacheData app =
        entry(result.getCacheResults().get(HaProxyResourceType.APPLICATION.name()), appKey);
    assertThat(app.getRelationships().get(HaProxyResourceType.FRONTEND.name()))
        .containsExactly(frontendKey);

    CacheData cluster =
        entry(result.getCacheResults().get(HaProxyResourceType.CLUSTER.name()), clusterKey);
    assertThat(cluster.getAttributes())
        .containsEntry("name", "web-main")
        .containsEntry("accountName", "homelab")
        .containsEntry("app", "web");
    assertThat(cluster.getRelationships().get(HaProxyResourceType.FRONTEND.name()))
        .containsExactly(frontendKey);

    // A frontend without metadata still gets an application via the Frigga name fallback.
    CacheData unmanaged =
        entry(frontends, HaProxyCacheKeys.frontend("homelab", "dc1", "unmanaged_frontend"));
    assertThat(unmanaged.getAttributes()).containsEntry("spinnakerApp", "unmanaged_frontend");
    assertThat(unmanaged.getRelationships().get(HaProxyResourceType.APPLICATION.name()))
        .containsExactly(HaProxyCacheKeys.application("unmanaged_frontend"));
  }

  @Test
  void backendAgentCachesBackendsWithEmbeddedServersAndRuntimeHealth() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "[{\"name\":\"web-main-v001\",\"mode\":\"http\","
                    + "\"metadata\":{\"spinnaker-app\":\"web\",\"spinnaker-cluster\":\"web-main\"},"
                    + "\"servers\":{\"web001\":{\"name\":\"web001\",\"address\":\"10.0.0.11\",\"port\":8080}}}]"));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "[{\"name\":\"web001\",\"address\":\"10.0.0.11\",\"port\":8080,"
                    + "\"admin_state\":\"ready\",\"operational_state\":\"up\"},"
                    + "{\"name\":\"web002\",\"address\":\"10.0.0.12\",\"port\":8080,"
                    + "\"admin_state\":\"maint\",\"operational_state\":\"down\"}]"));

    BackendCachingAgent agent = new BackendCachingAgent(credentials, registry, namer);
    CacheResult result = agent.loadData(null);

    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v3/services/haproxy/configuration/backends?full_section=true");
    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v3/services/haproxy/runtime/backends/web-main-v001/servers");

    String backendKey = HaProxyCacheKeys.backend("homelab", "dc1", "web-main-v001");
    CacheData backend =
        entry(result.getCacheResults().get(HaProxyResourceType.BACKEND.name()), backendKey);
    assertThat(backend.getAttributes()).containsEntry("spinnakerApp", "web");

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> servers =
        (Map<String, Map<String, Object>>) backend.getAttributes().get("servers");
    assertThat(servers).containsKey("web001");
    assertThat(servers.get("web001")).containsEntry("address", "10.0.0.11");

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> runtimeServers =
        (Map<String, Map<String, Object>>) backend.getAttributes().get("runtime_servers");
    assertThat(runtimeServers.get("web001")).containsEntry("operational_state", "up");
    assertThat(runtimeServers.get("web002")).containsEntry("admin_state", "maint");

    assertThat(backend.getRelationships().get(HaProxyResourceType.CLUSTER.name()))
        .containsExactly(HaProxyCacheKeys.cluster("homelab", "web-main"));

    // One HEALTH entry per runtime server, related back to the backend.
    var healthEntries = result.getCacheResults().get(HaProxyResourceType.HEALTH.name());
    assertThat(healthEntries).hasSize(2);
    CacheData up =
        entry(healthEntries, HaProxyCacheKeys.health("homelab", "dc1", "web-main-v001", "web001"));
    assertThat(up.getAttributes())
        .containsEntry("state", "Up")
        .containsEntry("address", "10.0.0.11")
        .containsEntry("backend", "web-main-v001");
    CacheData maint =
        entry(healthEntries, HaProxyCacheKeys.health("homelab", "dc1", "web-main-v001", "web002"));
    assertThat(maint.getAttributes()).containsEntry("state", "OutOfService");
    assertThat(maint.getRelationships().get(HaProxyResourceType.BACKEND.name()))
        .containsExactly(backendKey);
  }

  @Test
  void failedFetchesYieldEmptyCacheResults() {
    server.enqueue(new MockResponse().setResponseCode(500));

    FrontendCachingAgent agent = new FrontendCachingAgent(credentials, registry, namer);
    CacheResult result = agent.loadData(null);

    assertThat(result.getCacheResults().get(HaProxyResourceType.FRONTEND.name())).isEmpty();
  }
}
