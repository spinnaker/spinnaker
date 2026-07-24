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
package com.netflix.spinnaker.clouddriver.proxmox.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.config.ProxmoxConfigurationProperties;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxmoxNamedAccountCredentialsTest {

  private MockWebServer server;
  private ProxmoxConfigurationProperties.ProxmoxManagedAccount account;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    account = new ProxmoxConfigurationProperties.ProxmoxManagedAccount();
    account.setName("test-account");
    account.setServer(server.getHostName());
    account.setPort(server.getPort());
    account.setScheme("http"); // MockWebServer speaks plain HTTP
    account.setUserName("root@pam");
    account.setPassword("secret");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  // ── initial ticket fetch ───────────────────────────────────────────────────

  @Test
  void initialTicketFetchedOnConstruction() throws Exception {
    server.enqueue(ticketResponse("t1", "csrf1"));

    new ProxmoxNamedAccountCredentials(account);

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getPath()).isEqualTo("/api2/json/access/ticket");
    assertThat(req.getMethod()).isEqualTo("POST");
  }

  // ── happy path: ticket headers on every request ────────────────────────────

  @Test
  void requestCarriesInitialTicketHeaders() throws Exception {
    server.enqueue(ticketResponse("t1", "csrf1"));
    server.enqueue(nodesOkResponse());

    ProxmoxNamedAccountCredentials credentials = new ProxmoxNamedAccountCredentials(account);
    credentials.getApiService().getNodes().execute();

    server.takeRequest(); // startup ticket POST
    RecordedRequest nodesReq = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(nodesReq.getHeader("Cookie")).isEqualTo("PVEAuthCookie=t1");
    assertThat(nodesReq.getHeader("CSRFPreventionToken")).isEqualTo("csrf1");
  }

  // ── 401 triggers refresh and retry ────────────────────────────────────────

  @Test
  void ticketRefreshedOn401AndRequestRetried() throws Exception {
    server.enqueue(ticketResponse("t1", "csrf1")); // startup
    server.enqueue(new MockResponse().setResponseCode(401)); // first nodes call → expired
    server.enqueue(ticketResponse("t2", "csrf2")); // authenticator refreshes
    server.enqueue(nodesOkResponse()); // retry succeeds

    ProxmoxNamedAccountCredentials credentials = new ProxmoxNamedAccountCredentials(account);
    retrofit2.Response<?> resp = credentials.getApiService().getNodes().execute();

    assertThat(resp.isSuccessful()).isTrue();
    assertThat(server.getRequestCount()).isEqualTo(4);

    server.takeRequest(); // startup ticket
    server.takeRequest(); // first nodes (401)
    server.takeRequest(); // refresh ticket
    RecordedRequest retryReq = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(retryReq.getHeader("Cookie")).isEqualTo("PVEAuthCookie=t2");
    assertThat(retryReq.getHeader("CSRFPreventionToken")).isEqualTo("csrf2");
  }

  // ── refreshed ticket is used for all subsequent requests ──────────────────

  @Test
  void subsequentRequestsUseRefreshedTicket() throws Exception {
    server.enqueue(ticketResponse("t1", "csrf1")); // startup
    server.enqueue(new MockResponse().setResponseCode(401)); // first call → expired
    server.enqueue(ticketResponse("t2", "csrf2")); // refresh
    server.enqueue(nodesOkResponse()); // retry (t2)
    server.enqueue(nodesOkResponse()); // second independent call

    ProxmoxNamedAccountCredentials credentials = new ProxmoxNamedAccountCredentials(account);
    credentials.getApiService().getNodes().execute(); // triggers refresh internally
    credentials.getApiService().getNodes().execute(); // should already carry t2

    server.takeRequest(); // startup ticket
    server.takeRequest(); // first nodes (401)
    server.takeRequest(); // refresh ticket
    server.takeRequest(); // retry with t2
    RecordedRequest secondCall = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(secondCall.getHeader("Cookie")).isEqualTo("PVEAuthCookie=t2");
  }

  // ── double 401: authenticator gives up after one retry ────────────────────

  @Test
  void givesUpAfterDoubleAuthFailure() throws Exception {
    server.enqueue(ticketResponse("t1", "csrf1")); // startup
    server.enqueue(new MockResponse().setResponseCode(401)); // nodes → 401
    server.enqueue(ticketResponse("t2", "csrf2")); // refresh succeeds
    server.enqueue(new MockResponse().setResponseCode(401)); // retry also 401 → give up

    ProxmoxNamedAccountCredentials credentials = new ProxmoxNamedAccountCredentials(account);
    retrofit2.Response<?> resp = credentials.getApiService().getNodes().execute();

    assertThat(resp.isSuccessful()).isFalse();
    assertThat(resp.code()).isEqualTo(401);
    // exactly 4 exchanges: startup ticket, first nodes, refresh ticket, retry nodes
    assertThat(server.getRequestCount()).isEqualTo(4);
  }

  // ── API token auth ─────────────────────────────────────────────────────────

  @Test
  void apiTokenAuthSendsAuthorizationHeader() throws Exception {
    account.setUserName(null);
    account.setPassword(null);
    account.setApiToken("user@realm!tokenid=secret-uuid");
    server.enqueue(nodesOkResponse());

    ProxmoxNamedAccountCredentials credentials = new ProxmoxNamedAccountCredentials(account);
    credentials.getApiService().getNodes().execute();

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getHeader("Authorization"))
        .isEqualTo("PVEAPIToken=user@realm!tokenid=secret-uuid");
    // no startup ticket fetch — exactly one request total
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static MockResponse ticketResponse(String ticket, String csrf) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            "{\"data\":{\"ticket\":\"" + ticket + "\",\"CSRFPreventionToken\":\"" + csrf + "\"}}");
  }

  private static MockResponse nodesOkResponse() {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"data\":[{\"node\":\"pve01\",\"status\":\"online\"}]}");
  }
}
