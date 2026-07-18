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
package com.netflix.spinnaker.clouddriver.haproxy;

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
import org.junit.jupiter.api.*;
import retrofit2.Response;

/** Runs against a live HAProxy Data Plane API (v3). Fill in the connection details and enable. */
@Tag("integration")
@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HaProxyApiIntegrationTest {

  private static final String SERVER = ""; // <-- replace as needed
  private static final int PORT = 5555;
  private static final String USERNAME = "admin";
  private static final String PASSWORD = ""; // <-- fill in before running

  private HaProxyNamedAccountCredentials credentials;

  @BeforeAll
  void setup() {
    HaProxyConfigurationProperties.HaProxyManagedAccount account =
        new HaProxyConfigurationProperties.HaProxyManagedAccount();
    account.setName("integration-test");
    account.setServer(SERVER);
    account.setPort(PORT);
    account.setUserName(USERNAME);
    account.setPassword(PASSWORD);
    credentials = new HaProxyNamedAccountCredentials(account);
  }

  @Test
  void infoIsLoaded() throws Exception {
    Response<Info> response = credentials.getApi(InformationApi.class).getInfo().execute();

    assertThat(response.isSuccessful()).isTrue();
    Info info = response.body();
    assertThat(info.getApi()).isNotNull();
    System.out.printf(
        "=== Data Plane API ===%n  version=%s host=%s%n",
        info.getApi().getVersion(),
        info.getSystem() != null ? info.getSystem().getHostname() : "?");
  }

  @Test
  void frontendsAreLoadedWithBinds() throws Exception {
    Response<List<Frontend>> response =
        credentials.getApi(FrontendApi.class).getFrontends(null, true).execute();

    assertThat(response.isSuccessful())
        .as("GET /frontends returned HTTP %d", response.code())
        .isTrue();
    List<Frontend> frontends = response.body();
    assertThat(frontends).isNotNull();

    System.out.println("=== Frontends ===");
    frontends.forEach(
        f -> {
          System.out.printf(
              "  frontend=%-30s mode=%-5s default_backend=%-30s metadata=%s%n",
              f.getName(), f.getMode(), f.getDefaultBackend(), f.getMetadata());
          if (f.getBinds() != null) {
            f.getBinds()
                .forEach(
                    (name, bind) ->
                        System.out.printf(
                            "    bind=%-15s %s:%s%n", name, bind.getAddress(), bind.getPort()));
          }
        });
  }

  @Test
  void backendsAndServersAreLoaded() throws Exception {
    Response<List<Backend>> response =
        credentials.getApi(BackendApi.class).getBackends(null, false).execute();

    assertThat(response.isSuccessful())
        .as("GET /backends returned HTTP %d", response.code())
        .isTrue();
    List<Backend> backends = response.body();
    assertThat(backends).isNotNull();

    System.out.println("=== Backends ===");
    for (Backend backend : backends) {
      System.out.printf(
          "  backend=%-30s mode=%-5s metadata=%s%n",
          backend.getName(), backend.getMode(), backend.getMetadata());

      Response<List<Server>> serversResponse =
          credentials
              .getApi(ServerApi.class)
              .getAllServerBackend(backend.getName(), null)
              .execute();
      assertThat(serversResponse.isSuccessful())
          .as(
              "GET /backends/%s/servers returned HTTP %d",
              backend.getName(), serversResponse.code())
          .isTrue();
      serversResponse
          .body()
          .forEach(
              s ->
                  System.out.printf(
                      "    server=%-20s %s:%s check=%s%n",
                      s.getName(), s.getAddress(), s.getPort(), s.getCheck()));
    }
  }
}
