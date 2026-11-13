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
package com.netflix.spinnaker.clouddriver.haproxy.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.BackendApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.FrontendApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.InformationApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.ServerApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Backend;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Frontend;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Info;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Server;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyNamedAccountCredentials;
import com.netflix.spinnaker.config.HaProxyConfigurationProperties;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the generated Data Plane API client (v3) against canned responses, pinning down the
 * request shapes (paths, auth) and response parsing this provider relies on.
 */
class HaProxyDataPlaneApiMockTest {

  private MockWebServer server;
  private HaProxyNamedAccountCredentials credentials;

  @BeforeEach
  void setup() throws Exception {
    server = new MockWebServer();
    server.start();

    HaProxyConfigurationProperties.HaProxyManagedAccount account =
        new HaProxyConfigurationProperties.HaProxyManagedAccount();
    account.setName("test");
    account.setServer(server.getHostName());
    account.setPort(server.getPort());
    account.setScheme("http");
    account.setUserName("admin");
    account.setPassword("secret");
    credentials = new HaProxyNamedAccountCredentials(account);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void frontendsAreFetchedWithBasicAuthAndParsed() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "[{\"name\":\"web-main\",\"mode\":\"http\",\"default_backend\":\"web-main-v001\","
                    + "\"metadata\":{\"spinnaker-app\":\"web\"},"
                    + "\"binds\":{\"public\":{\"name\":\"public\",\"address\":\"*\",\"port\":443,\"ssl\":true}}}]"));

    List<Frontend> frontends =
        credentials.getApi(FrontendApi.class).getFrontends(null, true).execute().body();

    assertThat(frontends).hasSize(1);
    Frontend frontend = frontends.get(0);
    assertThat(frontend.getName()).isEqualTo("web-main");
    assertThat(frontend.getDefaultBackend()).isEqualTo("web-main-v001");
    assertThat(frontend.getMetadata()).containsEntry("spinnaker-app", "web");
    assertThat(frontend.getBinds()).containsKey("public");
    assertThat(frontend.getBinds().get("public").getPort()).isEqualTo(443);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath())
        .isEqualTo("/v3/services/haproxy/configuration/frontends?full_section=true");
    assertThat(request.getHeader("Authorization")).startsWith("Basic ");
  }

  @Test
  void backendsAndServersAreFetchedAndParsed() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "[{\"name\":\"web-main-v001\",\"mode\":\"http\","
                    + "\"balance\":{\"algorithm\":\"roundrobin\"},"
                    + "\"metadata\":{\"spinnaker-cluster\":\"web-main\"}}]"));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "[{\"name\":\"web001\",\"address\":\"10.0.0.11\",\"port\":8080,\"check\":\"enabled\"}]"));

    List<Backend> backends =
        credentials.getApi(BackendApi.class).getBackends(null, false).execute().body();
    assertThat(backends).hasSize(1);
    assertThat(backends.get(0).getName()).isEqualTo("web-main-v001");
    assertThat(backends.get(0).getMetadata()).containsEntry("spinnaker-cluster", "web-main");

    List<Server> servers =
        credentials
            .getApi(ServerApi.class)
            .getAllServerBackend("web-main-v001", null)
            .execute()
            .body();
    assertThat(servers).hasSize(1);
    assertThat(servers.get(0).getAddress()).isEqualTo("10.0.0.11");
    assertThat(servers.get(0).getPort()).isEqualTo(8080);

    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v3/services/haproxy/configuration/backends?full_section=false");
    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v3/services/haproxy/configuration/backends/web-main-v001/servers");
  }

  @Test
  void infoIsFetchedAndParsed() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"api\":{\"version\":\"3.4.0\"},\"system\":{\"hostname\":\"lb1\"}}"));

    Info info = credentials.getApi(InformationApi.class).getInfo().execute().body();

    assertThat(info.getApi().getVersion()).isEqualTo("3.4.0");
    assertThat(server.takeRequest().getPath()).isEqualTo("/v3/info");
  }
}
