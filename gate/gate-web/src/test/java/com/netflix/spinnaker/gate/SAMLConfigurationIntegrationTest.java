/*
 * Copyright 2026 Mcintosh.farm
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

package com.netflix.spinnaker.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.gate.security.saml.SecuritySamlProperties;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import retrofit2.mock.Calls;

/**
 * Integration test for SAML configuration using Keycloak testcontainer. Tests that a metadata IS
 * loaded AND actually sets up a keycloak realm & user to validate this. TODO: The FULL Auth flow
 * requires a REAL browser. Work on the selenium tests to actually LOGIN.
 *
 * <p>This DOES create ALL the scaffolding the the realm, the client for spinnaker, then a user,
 * which lets spinnaker load the remote metadata. This then validates that the login flow would work
 * IF we wanted to go the extra step of using a selenium browser or such and work around...
 * headaches of trying to figure out the networking for ALL of these pieces to work appropriately
 * together
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "saml.enabled=true",
      "saml.issuer-id=spinnaker-test", // MUST match client-id in keycloak
      "saml.sign-requests=true", // Keycloak forces spring to sign requests even if keycloak doesn't
      // validate the signature.
      "saml.signing-credentials[0].privateKeyLocation=private_key.pem",
      "saml.signing-credentials[0].certificateLocation=certificate.pem",
      "management.endpoints.web.exposure.include=beans" // used as an authenticated endpoint to
      // validate auth stuff
    },
    classes = Main.class)
class SAMLConfigurationIntegrationTest {

  /**
   * To prevent the background thread that refreshes the applications cache, which makes calls to
   * clouddriver and front50 that fail and pollute the logs because those services are not
   * available.
   */
  @MockBean ApplicationService applicationService;

  @MockBean ClouddriverService clouddriverService;

  @MockBean FiatService fiatService;

  @MockBean Front50Service front50Service;

  @BeforeEach
  void setup() {
    when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));
    when(front50Service.getServiceAccounts()).thenReturn(Calls.response(List.of()));
  }

  private static final String REALM_NAME = "test-realm";
  private static final String CLIENT_ID = "spinnaker-test";
  private static final String TEST_USER = "testuser";
  private static final String TEST_PASSWORD = "testpassword";
  private static final String TEST_EMAIL = "testuser@example.com";

  @LocalServerPort private int port;

  @Container
  private static final GenericContainer<?> keycloak =
      new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:23.0"))
          .withEnv("KEYCLOAK_ADMIN", "admin")
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
          .withCommand("start-dev --health-enabled=true")
          .withNetwork(Network.newNetwork())
          .withExposedPorts(8080)
          .waitingFor(Wait.forHttp("/health/ready").forPort(8080).forStatusCode(200))
          .withStartupTimeout(Duration.ofMinutes(3));

  private static String keycloakBaseUrl;

  @BeforeAll
  static void setUpKeycloak() throws Exception {
    keycloak.start();

    // Expose the Keycloak port to the host for testing
    Integer mappedPort = keycloak.getMappedPort(8080);
    Testcontainers.exposeHostPorts(mappedPort);

    keycloakBaseUrl = String.format("http://localhost:%d", mappedPort);

    System.out.println("keycloakBaseUrl: " + keycloakBaseUrl);

    // Initialize Keycloak with realm and client
    configureKeycloakRealm();
  }

  @AfterAll
  static void cleanup() throws IOException {
    keycloak.stop();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
    registry.add(
        "saml.metadata-url",
        () -> keycloakBaseUrl + "/realms/" + REALM_NAME + "/protocol/saml/descriptor");
  }

  private static void configureKeycloakRealm() throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    // Get admin access token
    String tokenEndpoint = keycloakBaseUrl + "/realms/master/protocol/openid-connect/token";
    String tokenBody = "client_id=admin-cli&username=admin&password=admin&grant_type=password";

    HttpRequest tokenRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
            .build();

    HttpResponse<String> tokenResponse =
        client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
    String accessToken = extractAccessToken(tokenResponse.body());

    // Create realm
    String realmJson =
        """
        {
          "realm":"%s",
          "enabled":true
        }
        """
            .formatted(REALM_NAME);

    HttpRequest createRealmRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(keycloakBaseUrl + "/admin/realms"))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(realmJson))
            .build();

    assertThat(client.send(createRealmRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(201);

    // Create SAML client
    String clientJson =
        """
        {
         "clientId":"%s",
         "enabled":true,
         "protocol":"saml",
         "redirectUris":["*"],
         "attributes":{
          "saml.authnstatement":"true",
          "saml.server.signature":"true",
          "saml.client.signature": "false"
         }
        }
        """
            .formatted(CLIENT_ID);

    HttpRequest createClientRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + REALM_NAME + "/clients"))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(clientJson))
            .build();

    assertThat(client.send(createClientRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(201);

    // Create test user
    String userJson =
        """
        {
          "username": "%s",
          "enabled": true,
          "credentials": [{"type": "password", "value": "%s", "temporary": false}],
          "attributes":
            {
              "email": ["%s"],
              "User.FirstName": ["Test"],
              "User.LastName": ["User"],
             "memberOf": ["user;admin"]
            }
        }
        """
            .formatted(TEST_USER, TEST_PASSWORD, TEST_EMAIL);

    HttpRequest createUserRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + REALM_NAME + "/users"))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(userJson))
            .build();

    HttpResponse<String> createUserResponse =
        client.send(createUserRequest, HttpResponse.BodyHandlers.ofString());
    assertThat(createUserResponse.statusCode()).isEqualTo(201);
  }

  private static String extractAccessToken(String json) {
    // Simple extraction of access_token from JSON response
    int start = json.indexOf("\"access_token\":\"") + 16;
    int end = json.indexOf("\"", start);
    return json.substring(start, end);
  }

  @Autowired private SecuritySamlProperties properties;

  @Test
  void contextLoads() {
    assertThat(properties).isNotNull();
    assertThat(properties.getMetadataUrl()).contains(REALM_NAME);
  }

  @Test
  void testRedirectAndWiringWorks() throws Exception {

    HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    // Make sure /auth/redirect hits the spring saml SP initiated auth flow. Spring redirects on any
    // given URL to the SP initiated redirect path /saml2/authenticate/<provider>
    // provider defaults to SSO in spinnaker.
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/redirect"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.headers().firstValue("Location").orElse(""))
        .isEqualTo(
            "http://localhost:"
                + port
                + "/saml2/authenticate/SSO"); // Redirect takes here, which then takes to the SAML
    // flows.
    assertThat(response.statusCode()).isEqualTo(302);

    // What's tricky next is that Spring on this endpoint DOES NOT do a "302".  It provides a form
    // with javascript to trigger a POST to the SAML IDP endpoint.  This
    // works with MORE browsers that don't support one of the REDIRECT's that support changing the
    // request type.
    // SO to validate this, we'll extract the response and verify it's contents.
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/saml2/authenticate/SSO"))
            .GET()
            .build();
    response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    /*
    Spring sends back some java script in the authenticate/sso path to do a redirect via a POST form submission.  THus... 200 response code.
    We'll check the body of the payload for the SAML request string instead.
     */
    String body = response.body();
    // Example of what we're looking for.  THIS Is a fairly standard set of form handling and
    // doesn't change much (it's from the RFC).  IF something changes
    // probably start gate, look at the endpoint via curl to show what could have changed.
    // <input type="hidden" name="SAMLRequest"
    // value="PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz48c2FtbDJwOkF1dGhuUmVxdWVzdCB4bWxuczpzYW1sMnA9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDpwcm90b2NvbCIgQXNzZXJ0aW9uQ29uc3VtZXJTZXJ2aWNlVVJMPSJodHRwOi8vbG9jYWxob3N0OjU0NDM2L3NhbWwvU1NPIiBEZXN0aW5hdGlvbj0iaHR0cHM6Ly9pbnRlZ3JhdG9yLTMzOTU3Njcub2t0YS5jb20vYXBwL2ludGVncmF0b3ItMzM5NTc2N19zcGlubmFrZXJfMS9leGt1MXJ4ZXloSmVQMTZpSjY5Ny9zc28vc2FtbCIgRm9yY2VBdXRobj0iZmFsc2UiIElEPSJBUlExMDkwNWQ3LWE4YTQtNDJmOC1hMDZlLWUwMjFhZjllZjc0YiIgSXNQYXNzaXZlPSJmYWxzZSIgSXNzdWVJbnN0YW50PSIyMDI2LTAzLTA1VDAzOjE0OjQ4LjYyMFoiIFByb3RvY29sQmluZGluZz0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmJpbmRpbmdzOkhUVFAtUE9TVCIgVmVyc2lvbj0iMi4wIj48c2FtbDI6SXNzdWVyIHhtbG5zOnNhbWwyPSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXNzZXJ0aW9uIj50ZXN0LXJlYWxtPC9zYW1sMjpJc3N1ZXI+PC9zYW1sMnA6QXV0aG5SZXF1ZXN0Pg=="/>
    String samlRequest = extractFieldFromBody("SAMLRequest", body);
    String relayState = extractFieldFromBody("RelayState", body);
    // Extract and decode SAML request

    // Verify SAML request contains expected elements
    String decodeSamlRequest = decodeSamlRequest(samlRequest);
    assertThat(decodeSamlRequest)
        .contains("AuthnRequest")
        .contains("AssertionConsumerServiceURL")
        .contains("http://localhost:" + port);

    // LOGIN to keycloak and get the auth payload.  This payload will be sent BACK to spinnaker.
    String samlRequestToKeycloak =
        generatePostBody(Map.of("SAMLRequest", samlRequest, "RelayState", relayState));
    HttpRequest authPost =
        HttpRequest.newBuilder()
            .uri(URI.create(keycloakBaseUrl + "/realms/" + REALM_NAME + "/protocol/saml"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(samlRequestToKeycloak))
            .build();
    // we're going to start using a client that DOES follow redirects now... to handle quirks
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    HttpResponse<String> authResponse = client.send(authPost, HttpResponse.BodyHandlers.ofString());
    assertThat(authResponse.statusCode())
        .isEqualTo(200); // This is now the "login" page for keycloak... aka auth redirect WORKED
    // Instead of continuing to create a browser flow next test will use html unit to really do all
    // this auth stuff.  The above
    // should allow someone to get an UNDERSTANDING of the SAML flow in spinnaker.  AS WELL as basic
    // validation of saml
    // configuration
  }

  private static @NotNull String extractFieldFromBody(String fieldName, String body) {
    int samlRequestIndex = body.indexOf(fieldName + "\" value=\"");
    assertThat(samlRequestIndex).isGreaterThan(0);
    String startOfBody = body.substring(samlRequestIndex + (fieldName + "\" value=\"").length());
    int endOfBody = startOfBody.indexOf("\"/");
    return startOfBody.substring(0, endOfBody);
  }

  private String generatePostBody(Map<String, String> data) {
    // 2. Encode the data to application/x-www-form-urlencoded format
    return data.entrySet().stream()
        .map(
            entry ->
                URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  // CONCEPTUALLY this could work with some tweaking to work but it SUCKS.  E.g. the
  // port is dynamic for Keycloak, but without  some... docker fiddling this is painful to get
  // working... See: https://github.com/testcontainers/testcontainers-java/issues/9922
  // The ALTERNATIVE is to actually install a chrome binary and run these tests without a test
  // container
  // this would bypass the docker network headaches.  FOR NOW... we're doing the flow MANUALLY up
  // above.

  @Test
  void testFullSamlAuthenticationFlowUsingABrowser() throws Exception {
    // Setup our user to be logged in.
    when(fiatService.loginUser(TEST_EMAIL)).thenReturn(Calls.response((Void) null));
    when(fiatService.getUserPermission(TEST_EMAIL))
        .thenReturn(
            Calls.response(
                new UserPermission.View()
                    .setName(TEST_EMAIL)
                    .setAdmin(false)
                    .setAccounts(
                        Set.of(
                            new Account.View()
                                .setName("test-account")
                                .setAuthorizations(ImmutableSet.of(Authorization.WRITE))))
                    .setRoles(
                        Set.of(new Role.View().setName("testRole").setSource(Role.Source.LDAP)))));
    // Using HtmlUnitDriver as it is "good enough" without requiring any additional installs of a
    // full browser
    WebDriver driver = new HtmlUnitDriver(true);
    // Navigate to protected resource which should trigger SAML authentication flow
    driver.get("http://localhost:" + port + "/beans");

    // Wait for Keycloak login page
    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    try {
      wait.until(ExpectedConditions.presenceOfElementLocated(By.id("username")));
    } catch (Exception e) {
      System.out.println(
          "Username should be on here: "
              + driver.getPageSource()
              + "... we may have hit an issue loading?");
    }

    // Fill in credentials
    driver.findElement(By.id("username")).sendKeys(TEST_USER);
    driver.findElement(By.id("password")).sendKeys(TEST_PASSWORD);
    driver.findElement(By.id("kc-login")).click();

    // Wait for redirect back to application.  Webdriver we can't tell much so pay attention to the
    // contents.  Info endpoint as an auth'd endpoint SHOULD expose some data we can query
    wait.until(ExpectedConditions.urlContains("localhost:" + port));

    // Verify we're back at the application
    String currentUrl = driver.getCurrentUrl();
    assertThat(currentUrl).contains("localhost:" + port + "/beans");
    assertThat(driver.getPageSource()).contains("beans");

    // Check auth user to make sure login REALLY worked
    driver.navigate().to("http://localhost:" + port + "/auth/user");
    assertThat(driver.getPageSource()).contains("username\":\"" + TEST_USER + "\"");
  }

  private String decodeSamlRequest(String samlRequest) {
    // SAML requests are typically deflate-compressed, but for simplicity we'll just decode
    return new String(Base64.getDecoder().decode(samlRequest), StandardCharsets.UTF_8);
  }
}
