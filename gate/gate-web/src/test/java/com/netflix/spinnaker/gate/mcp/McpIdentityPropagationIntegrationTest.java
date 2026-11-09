/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import redis.clients.jedis.JedisPool;

/**
 * Drives a real {@code /mcp} streamable-HTTP request and records what a tool handler sees: whether
 * the caller's {@link org.springframework.security.core.context.SecurityContext} and the {@link
 * AuthenticatedRequest} MDC user are visible on the thread that runs the tool, and whether method
 * security ({@code @PreAuthorize}) sees the caller. Everything the gate-mcp tools do (audit user,
 * {@code X-SPINNAKER-USER} to downstream services, Fiat checks) relies on this.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    classes = {Main.class, McpIdentityPropagationIntegrationTest.TestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "security.basicform.enabled=true",
      "spring.security.user.name=testuser",
      "spring.security.user.password=testpassword",
      "mcp.server.enabled=true",
      "mcp.server.read-only=false",
      "spring.ai.mcp.server.protocol=STREAMABLE",
    })
class McpIdentityPropagationIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired TestRestTemplate restTemplate;

  @MockitoBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;
  @MockitoBean ApplicationService applicationService;
  @MockitoBean DefaultProviderLookupService defaultProviderLookupService;
  @MockitoBean JedisPool jedisPool;

  /**
   * With {@code fiat.enabled=false} the real evaluator treats everyone as admin, which would make
   * {@code @PreAuthorize} untestable. This mock denies, and records who the evaluator was asked
   * about.
   */
  @MockitoBean FiatPermissionEvaluator fiatPermissionEvaluator;

  @Test
  void toolHandlerSeesCaller() throws Exception {
    JsonNode result = callTool("whoami");

    String text = result.path("result").path("content").path(0).path("text").asText();
    assertThat(text)
        .as("tool handler should see the authenticated caller on both thread-locals")
        .isEqualTo("security=testuser;mdc=testuser");
  }

  @Test
  void preAuthorizeSeesCaller() throws Exception {
    java.util.concurrent.atomic.AtomicReference<String> askedAbout =
        new java.util.concurrent.atomic.AtomicReference<>();
    when(fiatPermissionEvaluator.isAdmin())
        .thenAnswer(
            inv -> {
              Authentication a = SecurityContextHolder.getContext().getAuthentication();
              askedAbout.set(a == null ? "none" : a.getName());
              return false;
            });

    JsonNode result = callTool("adminOnly");

    // The test user is not a Fiat admin, so the call must be denied rather than executed. If the
    // security context were lost it would also be denied, so the message distinguishes the two.
    assertThat(result.path("result").path("isError").asBoolean(false)).isTrue();
    assertThat(result.toString()).doesNotContain("EXECUTED");
    assertThat(askedAbout.get())
        .as("evaluator ran with the caller's identity")
        .isEqualTo("testuser");
  }

  private JsonNode callTool(String name) throws Exception {
    var client = restTemplate.withBasicAuth("testuser", "testpassword");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(HttpHeaders.ACCEPT, "application/json, text/event-stream");

    var init =
        client.postForEntity(
            "/mcp",
            new HttpEntity<>(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                    + "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                    + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}",
                headers),
            String.class);
    assertThat(init.getStatusCode().is2xxSuccessful()).as(String.valueOf(init)).isTrue();
    String sessionId = init.getHeaders().getFirst("Mcp-Session-Id");
    if (sessionId != null) {
      headers.set("Mcp-Session-Id", sessionId);
    }
    headers.set("MCP-Protocol-Version", "2025-06-18");
    client.postForEntity(
        "/mcp",
        new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", headers),
        String.class);

    var call =
        client.postForEntity(
            "/mcp",
            new HttpEntity<>(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\""
                    + name
                    + "\",\"arguments\":{}}}",
                headers),
            String.class);
    String body = call.getBody();
    // Streamable HTTP may answer as an SSE stream; pull out the JSON payload line.
    for (String line : body.split("\n")) {
      String l = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
      if (l.startsWith("{")) {
        return MAPPER.readTree(l);
      }
    }
    throw new AssertionError("no JSON-RPC payload in: " + body);
  }

  @Configuration
  static class TestConfiguration {
    @Bean
    ProbeTools probeTools() {
      return new ProbeTools();
    }
  }

  static class ProbeTools {
    @McpTool(name = "whoami", description = "reports the identity visible to the handler")
    public String whoami() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      return "security="
          + (auth == null ? "none" : auth.getName())
          + ";mdc="
          + AuthenticatedRequest.getSpinnakerUser().orElse("none");
    }

    @McpTool(name = "adminOnly", description = "admin-gated probe")
    @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
    public String adminOnly() {
      return "EXECUTED";
    }
  }
}
