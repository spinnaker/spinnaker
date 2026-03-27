package com.netflix.spinnaker.gate.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.Fault;
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
    HttpResponse<String> response = callGateWithPath("/admin", "GET");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(200);
    Map<String, Object> responseData = new ObjectMapper().readValue(response.body(), Map.class);
    assertThat(responseData).contains(entry("isAdmin", true), entry("username", "testuser"));
  }

  @Test
  public void verifyCanCallOrcaKillATask() throws Exception {
    // Orca's PUT /admin/forceCancelExecution returns void, so the response body is empty.
    wmOrca.stubFor(
        WireMock.put(
                urlEqualTo(
                    "/admin/forceCancelExecution?executionId=randomExecutionId&executionType=PIPELINE&canceledBy=testuser"))
            .willReturn(aResponse().withStatus(200).withResponseBody(Body.none())));
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> true);
    HttpResponse<String> response =
        callGateWithPath(
            "/admin/executions/forceCancel?executionId=randomExecutionId&executionType=PIPELINE",
            "PUT");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  public void rehydrateAExecution() throws Exception {
    setupOrcaMock();
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> true);
    HttpResponse<String> response =
        callGateWithPath(
            "/admin/executions/hydrate?executionId=randomExecutionId&dryRun=false", "POST");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  public void killZombieNetworkError() throws Exception {
    // When Orca causes an IOException (e.g. connection reset), killZombie catches it and wraps it
    // in a generic SpinnakerException, hiding the actual cause from the caller.
    wmOrca.stubFor(
        WireMock.put(
                urlEqualTo(
                    "/admin/forceCancelExecution?executionId=randomExecutionId&executionType=PIPELINE&canceledBy=testuser"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> true);
    HttpResponse<String> response =
        callGateWithPath(
            "/admin/executions/forceCancel?executionId=randomExecutionId&executionType=PIPELINE",
            "PUT");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(500);
    Map<String, Object> responseBody =
        new ObjectMapper().readValue(response.body(), new TypeReference<>() {});
    // AdminController.killZombie wraps the exception in a SpinnakerException with the message
    // "Error invoking killing of the zombie pipeline!..." but Spring's
    // ExceptionHandlerExceptionResolver walks the cause chain and finds
    // SpinnakerNetworkException, which SpinnakerRetrofitExceptionHandlers handles directly.
    // The SpinnakerException wrapper and its message are never used.
    assertThat(responseBody.get("exception"))
        .isEqualTo("com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException");
    assertThat(responseBody.get("message"))
        .isEqualTo(
            "Error invoking killing of the zombie pipeline!  Check logs - particularly on orca for more information: "
                + "java.net.SocketException: Connection reset");
  }

  @Test
  public void killZombieOrcaNotFound() throws Exception {
    // When Orca returns a 404, killZombie catches the resulting exception and wraps it in a
    // SpinnakerException. But Spring's ExceptionHandlerExceptionResolver walks the cause chain,
    // finds SpinnakerHttpException, and SpinnakerRetrofitExceptionHandlers handles it directly —
    // propagating the 404 status and Orca's error message. The SpinnakerException wrapper and its
    // "Error invoking killing of the zombie pipeline!" message are never used.
    wmOrca.stubFor(
        WireMock.put(
                urlEqualTo(
                    "/admin/forceCancelExecution?executionId=randomExecutionId&executionType=PIPELINE&canceledBy=testuser"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withBody(
                        "{\"error\":\"Not Found\",\"message\":\"Execution not found\",\"status\":404}")));
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> true);
    HttpResponse<String> response =
        callGateWithPath(
            "/admin/executions/forceCancel?executionId=randomExecutionId&executionType=PIPELINE",
            "PUT");
    assertNotNull(response);
    assertThat(response.statusCode()).isEqualTo(404);
    Map<String, Object> responseBody =
        new ObjectMapper().readValue(response.body(), new TypeReference<>() {});
    assertThat(responseBody.get("exception"))
        .isEqualTo("com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException");
    assertThat(responseBody.get("message"))
        .isEqualTo(
            "Error invoking killing of the zombie pipeline!  Check logs - particularly on orca for more information: "
                + "Status: 404, Method: PUT, URL: "
                + wmOrca.baseUrl()
                + "/admin/forceCancelExecution?executionId=randomExecutionId&executionType=PIPELINE&canceledBy=testuser, Message: Execution not found");
  }

  @Test
  public void verifyPermissionsDeniedIfNotAdmin() throws Exception {
    when(fiatPermissionEvaluator.isAdmin()).then(invocation -> false);
    HttpResponse<String> response =
        callGateWithPath("/admin/executions/forceCancel?executionId=randomExecutionId", "PUT");
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
  }
}
