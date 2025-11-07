package com.netflix.spinnaker.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.netflix.spinnaker.gate.services.PipelineService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "spring.security.user.name=testuser",
      "spring.security.user.password=testpassword",
      "security.basicform.enabled=true"
    })
public abstract class GateBootAuthIntegrationTest {

  private static final String TEST_USER = "testuser";

  private static final String TEST_PASSWORD = "testpassword";
  @LocalServerPort private int port;

  @Autowired ObjectMapper objectMapper;

  @MockBean PipelineService pipelineService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** to prevent period application loading */
  @MockBean ApplicationService applicationService;

  /** To prevent attempts to load accounts */
  @MockBean DefaultProviderLookupService defaultProviderLookupService;

  /** Generate a request to a gate endpoint that uses authorization and fails. */
  protected HttpResponse<String> callGateWithPath(String urlPath, String methodType)
      throws Exception {
    HttpClient client = HttpClient.newBuilder().build();

    URI uri = new URI("http://localhost:" + port + urlPath);
    String credentials = TEST_USER + ":" + TEST_PASSWORD;
    byte[] encodedCredentials =
        Base64.getEncoder().encode(credentials.getBytes(StandardCharsets.UTF_8));

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .method(methodType, HttpRequest.BodyPublishers.noBody())
            .header("Authorization", "Basic " + new String(encodedCredentials))
            .build();

    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
