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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import retrofit2.mock.Calls;

@Execution(ExecutionMode.SAME_THREAD)
abstract class AbstractSAMLConfigurationIntegrationTest {

  protected static final String REALM_NAME = "test-realm";
  protected static final String CLIENT_ID = "spinnaker-test";
  protected static final String TEST_USER = "testuser";
  protected static final String TEST_PASSWORD = "testpassword";
  protected static final String TEST_EMAIL = "testuser@example.com";
  // Registration ID — must match the key under
  // spring.security.saml2.relyingparty.registration.<id>
  protected static final String REGISTRATION_ID = "SSO";

  @MockitoBean ClouddriverService clouddriverService;

  @MockitoBean FiatService fiatService;

  @MockitoBean Front50Service front50Service;

  @Autowired protected SecuritySamlProperties samlProperties;

  @LocalServerPort protected int port;

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

  @BeforeEach
  void setupMocks() {
    when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));
    when(front50Service.getServiceAccounts()).thenReturn(Calls.response(List.of()));
  }

  @BeforeAll
  static void setUpKeycloak() throws Exception {
    keycloak.start();

    Integer mappedPort = keycloak.getMappedPort(8080);
    Testcontainers.exposeHostPorts(mappedPort);

    keycloakBaseUrl = String.format("http://localhost:%d", mappedPort);
    configureKeycloakRealm();
  }

  @AfterAll
  static void cleanup() throws IOException {
    keycloak.stop();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
    // IdP metadata URI uses Spring Boot's native SAML2 relying-party property.
    // The registration ID key must match the one used in @SpringBootTest properties.
    registry.add(
        "spring.security.saml2.relyingparty.registration."
            + REGISTRATION_ID
            + ".assertingparty.metadata-uri",
        () -> keycloakBaseUrl + "/realms/" + REALM_NAME + "/protocol/saml/descriptor");
  }

  protected void assertSamlPropertiesLoaded() {
    // Context loading successfully already proves Spring Boot auto-configured the relying-party
    // registration from the dynamic spring.security.saml2.* property.
    assertThat(samlProperties).isNotNull();
    assertThat(samlProperties.isEnabled()).isTrue();
  }

  protected void assertSamlRedirectFlowWorks() throws Exception {

    createSamlApplicationInKeycloak(port);
    HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/redirect"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.headers().firstValue("Location").orElse(""))
        .isEqualTo(
            "http://localhost:" + port + "/saml2/authenticate?registrationId=" + REGISTRATION_ID);
    assertThat(response.statusCode()).isEqualTo(302);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/saml2/authenticate/" + REGISTRATION_ID))
            .GET()
            .build();
    response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    String body = response.body();
    String samlRequest = extractFieldFromBody("SAMLRequest", body);
    String relayState = extractFieldFromBody("RelayState", body);

    String decodedSamlRequest = decodeSamlRequest(samlRequest);
    assertThat(decodedSamlRequest)
        .contains("AuthnRequest")
        .contains("AssertionConsumerServiceURL")
        // Spring Boot's default ACS path: /login/saml2/sso/{registrationId}
        .contains("http://localhost:" + port + "/saml/" + REGISTRATION_ID);

    String samlRequestToKeycloak =
        generatePostBody(Map.of("SAMLRequest", samlRequest, "RelayState", relayState));
    HttpRequest authPost =
        HttpRequest.newBuilder()
            .uri(URI.create(keycloakBaseUrl + "/realms/" + REALM_NAME + "/protocol/saml"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(samlRequestToKeycloak))
            .build();
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    HttpResponse<String> authResponse = client.send(authPost, HttpResponse.BodyHandlers.ofString());
    assertThat(authResponse.statusCode()).isEqualTo(200);
  }

  protected void assertFullSamlAuthenticationFlowUsingABrowserWorks() throws Exception {
    createSamlApplicationInKeycloak(port);

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

    WebDriver driver = new HtmlUnitDriver(true);
    driver.get("http://localhost:" + port + "/beans");

    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    System.out.println("Currently at :" + driver.getCurrentUrl());
    System.out.println("Current page:" + driver.getPageSource());
    wait.until(ExpectedConditions.presenceOfElementLocated(By.id("username")));

    driver.findElement(By.id("username")).sendKeys(TEST_USER);
    driver.findElement(By.id("password")).sendKeys(TEST_PASSWORD);
    driver.findElement(By.id("kc-login")).click();

    wait.until(ExpectedConditions.urlContains("localhost:" + port));

    System.out.println("After login, now at :" + driver.getCurrentUrl());
    System.out.println("After login, now at :" + driver.getPageSource());
    assertThat(driver.getCurrentUrl()).contains("localhost:" + port + "/beans");
    assertThat(driver.getPageSource()).contains("beans");

    driver.navigate().to("http://localhost:" + port + "/auth/user");
    assertThat(driver.getPageSource()).contains("username\":\"" + TEST_USER + "\"");
  }

  private static void configureKeycloakRealm() throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    String tokenEndpoint = keycloakBaseUrl + "/realms/master/protocol/openid-connect/token";
    String tokenBody = "client_id=admin-cli&username=admin&password=admin&grant_type=password";

    HttpRequest tokenRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
            .build();

    Thread.sleep(500);
    HttpResponse<String> tokenResponse =
        client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
    String accessToken = extractAccessToken(tokenResponse.body());

    String realmJson =
        """
        {
          "realm":"%s",
          "enabled":true
        }
        """
            .formatted(REALM_NAME);

    Thread.sleep(500);
    HttpRequest createRealmRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(keycloakBaseUrl + "/admin/realms"))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(realmJson))
            .build();
    assertThat(client.send(createRealmRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(201);

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

  private static void createSamlApplicationInKeycloak(int port)
      throws IOException, InterruptedException {

    HttpClient client = HttpClient.newHttpClient();

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

    // ACS redirect URI uses Spring Boot's default path: /login/saml2/sso/{registrationId}
    String clientJson =
        """
        {
         "clientId":"%s",
         "enabled":true,
         "protocol":"saml",
         "redirectUris":["http://localhost:%s/saml/%s"],
         "attributes":{
          "saml.authnstatement":"true",
          "saml.server.signature":"true",
          "saml.client.signature": "false"
         }
        }
        """
            .formatted(CLIENT_ID, port, REGISTRATION_ID);

    HttpRequest createClientRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + REALM_NAME + "/clients"))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(clientJson))
            .build();

    assertThat(client.send(createClientRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isIn(List.of(201, 409));
  }

  private static String extractAccessToken(String json) {
    int start = json.indexOf("\"access_token\":\"") + 16;
    int end = json.indexOf("\"", start);
    return json.substring(start, end);
  }

  private static @NotNull String extractFieldFromBody(String fieldName, String body) {
    String stringInTheBody = fieldName + "\" type=\"hidden\" value=\"";
    int samlRequestIndex = body.indexOf(stringInTheBody);
    assertThat(samlRequestIndex)
        .withFailMessage(
            "Expected the body to contain the request with a specific format {}, but actually got {} ",
            stringInTheBody,
            body)
        .isGreaterThan(0);
    String startOfBody = body.substring(samlRequestIndex + (stringInTheBody).length());
    int endOfBody = startOfBody.indexOf("\" /");
    return startOfBody.substring(0, endOfBody);
  }

  private String generatePostBody(Map<String, String> data) {
    return data.entrySet().stream()
        .map(
            entry ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private String decodeSamlRequest(String samlRequest) {
    return new String(Base64.getDecoder().decode(samlRequest), StandardCharsets.UTF_8);
  }
}
