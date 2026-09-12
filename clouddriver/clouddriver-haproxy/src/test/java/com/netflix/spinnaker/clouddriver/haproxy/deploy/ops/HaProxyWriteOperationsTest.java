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
package com.netflix.spinnaker.clouddriver.haproxy.deploy.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.haproxy.deploy.description.DeleteHaProxyLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.haproxy.deploy.description.EnableDisableHaProxyServerGroupDescription;
import com.netflix.spinnaker.clouddriver.haproxy.deploy.description.UpsertHaProxyLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyNamedAccountCredentials;
import com.netflix.spinnaker.config.HaProxyConfigurationProperties;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HaProxyWriteOperationsTest {

  private MockWebServer server;
  private HaProxyNamedAccountCredentials credentials;

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
    credentials = new HaProxyNamedAccountCredentials(account);

    TaskRepository.threadLocalTask.set(new DefaultTask("test-task"));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
    TaskRepository.threadLocalTask.remove();
  }

  private void enqueueJson(int code, String body) {
    server.enqueue(
        new MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body));
  }

  private UpsertHaProxyLoadBalancerDescription upsertDescription() {
    UpsertHaProxyLoadBalancerDescription description = new UpsertHaProxyLoadBalancerDescription();
    description.setCredentials(credentials);
    description.setName("web-main");
    description.setDefaultBackend("web-main-v001");
    description.setMetadata(Map.of("spinnaker-app", "web"));
    UpsertHaProxyLoadBalancerDescription.BindSpec bind =
        new UpsertHaProxyLoadBalancerDescription.BindSpec();
    bind.setAddress("*");
    bind.setPort(443);
    bind.setSsl(true);
    description.setBinds(Map.of("public", bind));
    return description;
  }

  @Test
  void upsertCreatesMissingFrontendInsideATransaction() throws Exception {
    enqueueJson(200, "12"); // configuration version
    enqueueJson(201, "{\"id\":\"tx1\",\"_version\":12,\"status\":\"in_progress\"}");
    enqueueJson(404, "{\"message\":\"missing\"}"); // frontend lookup
    enqueueJson(201, "{\"name\":\"web-main\"}"); // create
    enqueueJson(200, "{\"id\":\"tx1\",\"status\":\"success\"}"); // commit

    Map<String, Object> result =
        new UpsertHaProxyLoadBalancerAtomicOperation(upsertDescription()).operate(List.of());

    assertThat(result).containsKey("loadBalancers");

    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v3/services/haproxy/configuration/version");
    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v3/services/haproxy/transactions?version=12");
    assertThat(server.takeRequest().getPath())
        .isEqualTo(
            "/v3/services/haproxy/configuration/frontends/web-main?transaction_id=tx1&full_section=false");

    RecordedRequest create = server.takeRequest();
    assertThat(create.getMethod()).isEqualTo("POST");
    assertThat(create.getPath())
        .isEqualTo(
            "/v3/services/haproxy/configuration/frontends?transaction_id=tx1&full_section=true");
    String body = create.getBody().readUtf8();
    assertThat(body).contains("\"name\":\"web-main\"");
    assertThat(body).contains("\"default_backend\":\"web-main-v001\"");
    assertThat(body).contains("\"spinnaker-app\":\"web\"");
    assertThat(body).contains("\"port\":443");

    RecordedRequest commit = server.takeRequest();
    assertThat(commit.getMethod()).isEqualTo("PUT");
    assertThat(commit.getPath())
        .isEqualTo("/v3/services/haproxy/transactions/tx1?force_reload=true");
  }

  @Test
  void upsertReplacesExistingFrontend() throws Exception {
    enqueueJson(200, "12");
    enqueueJson(201, "{\"id\":\"tx1\",\"_version\":12,\"status\":\"in_progress\"}");
    enqueueJson(200, "{\"name\":\"web-main\"}"); // frontend exists
    enqueueJson(200, "{\"name\":\"web-main\"}"); // replace
    enqueueJson(200, "{\"id\":\"tx1\",\"status\":\"success\"}");

    new UpsertHaProxyLoadBalancerAtomicOperation(upsertDescription()).operate(List.of());

    server.takeRequest(); // version
    server.takeRequest(); // start transaction
    server.takeRequest(); // lookup
    RecordedRequest replace = server.takeRequest();
    assertThat(replace.getMethod()).isEqualTo("PUT");
    assertThat(replace.getPath())
        .isEqualTo(
            "/v3/services/haproxy/configuration/frontends/web-main?transaction_id=tx1&full_section=true");
  }

  @Test
  void commitConflictsAreRetriedFromAFreshVersion() throws Exception {
    // First attempt conflicts on commit.
    enqueueJson(200, "12");
    enqueueJson(201, "{\"id\":\"tx1\",\"_version\":12,\"status\":\"in_progress\"}");
    enqueueJson(404, "{}");
    enqueueJson(201, "{\"name\":\"web-main\"}");
    enqueueJson(406, "{\"message\":\"version mismatch\"}");
    enqueueJson(204, ""); // abandoned transaction delete
    // Second attempt succeeds.
    enqueueJson(200, "13");
    enqueueJson(201, "{\"id\":\"tx2\",\"_version\":13,\"status\":\"in_progress\"}");
    enqueueJson(404, "{}");
    enqueueJson(201, "{\"name\":\"web-main\"}");
    enqueueJson(200, "{\"id\":\"tx2\",\"status\":\"success\"}");

    new UpsertHaProxyLoadBalancerAtomicOperation(upsertDescription()).operate(List.of());

    assertThat(server.getRequestCount()).isEqualTo(11);
  }

  @Test
  void deleteRemovesTheFrontendInsideATransaction() throws Exception {
    enqueueJson(200, "12");
    enqueueJson(201, "{\"id\":\"tx1\",\"_version\":12,\"status\":\"in_progress\"}");
    enqueueJson(204, "");
    enqueueJson(200, "{\"id\":\"tx1\",\"status\":\"success\"}");

    DeleteHaProxyLoadBalancerDescription description = new DeleteHaProxyLoadBalancerDescription();
    description.setCredentials(credentials);
    description.setLoadBalancerName("web-main");
    new DeleteHaProxyLoadBalancerAtomicOperation(description).operate(List.of());

    server.takeRequest();
    server.takeRequest();
    RecordedRequest delete = server.takeRequest();
    assertThat(delete.getMethod()).isEqualTo("DELETE");
    assertThat(delete.getPath())
        .isEqualTo("/v3/services/haproxy/configuration/frontends/web-main?transaction_id=tx1");
  }

  @Test
  void disableSetsAllReadyServersToMaint() throws Exception {
    enqueueJson(
        200,
        "[{\"name\":\"web001\",\"admin_state\":\"ready\",\"operational_state\":\"up\"},"
            + "{\"name\":\"web002\",\"admin_state\":\"maint\",\"operational_state\":\"down\"}]");
    enqueueJson(200, "{\"name\":\"web001\",\"admin_state\":\"maint\"}");

    EnableDisableHaProxyServerGroupDescription description =
        new EnableDisableHaProxyServerGroupDescription();
    description.setCredentials(credentials);
    description.setServerGroupName("web-main-v001");
    new EnableDisableHaProxyServerGroupAtomicOperation(description, false).operate(List.of());

    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v3/services/haproxy/runtime/backends/web-main-v001/servers");
    // Only the ready server is transitioned; web002 is already in maint.
    RecordedRequest put = server.takeRequest();
    assertThat(put.getMethod()).isEqualTo("PUT");
    assertThat(put.getPath())
        .isEqualTo("/v3/services/haproxy/runtime/backends/web-main-v001/servers/web001");
    assertThat(put.getBody().readUtf8()).contains("\"admin_state\":\"maint\"");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }
}
