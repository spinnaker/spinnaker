package com.netflix.spinnaker.gate.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.GateBootAuthIntegrationTest;
import com.netflix.spinnaker.gate.Main;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = {Main.class, AdminController.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminControllerTest extends GateBootAuthIntegrationTest {
  @RegisterExtension
  static WireMockExtension wmOrca =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random ports into gate
    System.out.println("wiremock orca url: " + wmOrca.baseUrl());
    registry.add("services.orca.base-url", wmOrca::baseUrl);
  }

  @MockBean FiatPermissionEvaluator fiatPermissionEvaluator;

  @BeforeEach
  void setUp() {}

  @Test
  public void basicAdminCheck() throws Exception {
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> true);
    HttpResponse<String> response = callGateWithPath("/admin/", "GET");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(200);
    Map<String, Object> responseData = new ObjectMapper().readValue(response.body(), Map.class);
    assertThat(responseData).contains(entry("isAdmin", true), entry("username", "testuser"));
  }

  @Test
  public void verifyCanCallOracKillATask() throws Exception {
    setupOrcaMock();
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> true);
    HttpResponse<String> response =
        callGateWithPath("/admin/zombie/kill/randomExecutionId/PIPELINE", "POST");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  public void rehydrateAExecution() throws Exception {
    setupOrcaMock();
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> true);
    HttpResponse<String> response =
        callGateWithPath("/admin/zombie/hydrate/randomExecutionId/false", "POST");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  public void verifyPermissionsDeniedIfNotAdmin() throws Exception {
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> false);
    HttpResponse<String> response =
        callGateWithPath("/admin/zombie/kill/randomExecutionId/PIPELINE", "POST");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(403);
  }

  void setupOrcaMock() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    wmOrca.stubFor(
        WireMock.post(urlEqualTo("/admin/queue/hydrate?executionId=randomExecutionId&dryRun=false"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(objectMapper.writeValueAsString(Map.of("foo", "bar")))));

    // simulate Orca response to the delete request
    wmOrca.stubFor(
        WireMock.put(
                urlEqualTo(
                    "/admin/forceCancelExecution?executionId=randomExecutionId&executionType=PIPELINE&canceledBy=testuser"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(objectMapper.writeValueAsString(Map.of("foo", "bar")))));
  }
}
