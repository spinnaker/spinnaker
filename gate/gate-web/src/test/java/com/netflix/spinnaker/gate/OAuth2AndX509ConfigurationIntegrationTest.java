/*
 * Copyright 2026 Harness Inc.
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
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Integration test for OAuth2 + X509 (MTLS) configuration using Keycloak testcontainer.
 *
 * <p>This test verifies the critical requirement that OAuth2 and MTLS can work simultaneously:
 *
 * <ul>
 *   <li>OAuth2 authentication works on the primary port (standard HTTP authentication flow)
 *   <li>X509/MTLS authentication works on a separate secure port (mutual TLS with client
 *       certificates)
 * </ul>
 *
 * <p>The test sets up:
 *
 * <ul>
 *   <li>Keycloak container with OAuth2 configured (realm, client, user)
 *   <li>Two server ports: main port for OAuth2, additional SSL port for MTLS
 *   <li>Client and server certificates for mutual TLS authentication
 *   <li>Security filter chains for both authentication methods
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      // Enable OAuth2
      "spring.security.oauth2.client.registration.keycloak.client-id=spinnaker-client",
      "spring.security.oauth2.client.registration.keycloak.client-secret=client-secret",
      "spring.security.oauth2.client.registration.keycloak.scope=openid,profile,email",
      "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code",
      "spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
      "spring.security.oauth2.client.provider.keycloak.authorization-uri=http://localhost:8080/realms/test-realm/protocol/openid-connect/auth",
      "spring.security.oauth2.client.provider.keycloak.token-uri=http://localhost:8080/realms/test-realm/protocol/openid-connect/token",
      "spring.security.oauth2.client.provider.keycloak.user-info-uri=http://localhost:8080/realms/test-realm/protocol/openid-connect/userinfo",
      "spring.security.oauth2.client.provider.keycloak.user-name-attribute=preferred_username",
      // Enable X509
      "x509.enabled=true",
      "x509.subject-principal-regex=CN=(.*?)(?:,|$)",
      // Configure MTLS on a separate apiPort via kork's TomcatConfiguration
      // Main port: HTTPS without client cert requirement (for OAuth2)
      // API port (8444): HTTPS with client cert requirement (MTLS via kork)
      "server.ssl.enabled=true",
      "server.ssl.key-store-type=JKS",
      "server.ssl.key-store-password=changeit",
      "server.ssl.trust-store-type=JKS",
      "server.ssl.trust-store-password=changeit",
      "server.ssl.client-auth=none", // No client cert on main port
      "default.apiPort=8444", // API port will have client-auth=need via kork
      "management.endpoints.web.exposure.include=beans"
    },
    classes = Main.class)
class OAuth2AndX509ConfigurationIntegrationTest {

  protected static final String REALM_NAME = "test-realm";
  protected static final String CLIENT_ID = "spinnaker-client";
  protected static final String CLIENT_SECRET = "client-secret";
  protected static final String TEST_USER = "testuser";
  protected static final String TEST_PASSWORD = "testpassword";
  protected static final String TEST_EMAIL = "testuser@example.com";

  @MockitoBean ClouddriverService clouddriverService;

  @MockitoBean FiatService fiatService;

  @MockitoBean Front50Service front50Service;

  @LocalServerPort protected int mainPort;

  @Autowired private ListableBeanFactory beanFactory;

  @TempDir static File tempDir;

  private static File serverKeyStore;
  private static File serverTrustStore;
  private static File clientKeyStore;
  private static File clientTrustStore;
  private static String keystorePassword = "changeit";

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
  private static int mtlsPort = 8444;

  @BeforeEach
  void setupMocks() {
    when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));
    when(front50Service.getServiceAccounts()).thenReturn(Calls.response(List.of()));
  }

  @BeforeAll
  static void setUpKeycloakAndCertificates() throws Exception {
    keycloak.start();

    Integer mappedPort = keycloak.getMappedPort(8080);
    Testcontainers.exposeHostPorts(mappedPort, mtlsPort);

    keycloakBaseUrl = String.format("http://localhost:%d", mappedPort);

    // Generate certificates for MTLS
    generateCertificates();

    // Configure Keycloak with OAuth2
    configureKeycloakRealm();
  }

  @AfterAll
  static void cleanup() {
    keycloak.stop();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    String baseUrl = keycloakBaseUrl + "/realms/" + REALM_NAME + "/protocol/openid-connect";
    String issuerUri = keycloakBaseUrl + "/realms/" + REALM_NAME;

    registry.add(
        "spring.security.oauth2.client.provider.keycloak.authorization-uri",
        () -> baseUrl + "/auth");
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri", () -> baseUrl + "/token");
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.user-info-uri",
        () -> baseUrl + "/userinfo");
    registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> issuerUri);

    // Configure SSL keystores dynamically
    registry.add("server.ssl.key-store", () -> "file:" + serverKeyStore.getAbsolutePath());
    registry.add("server.ssl.trust-store", () -> "file:" + serverTrustStore.getAbsolutePath());
  }

  @Test
  void fullX509AuthenticationFlowWithClientCertificateWorks() throws Exception {
    // Mock Fiat responses for user authorization
    // The X509 filter extracts username from the certificate CN (testclient)
    when(fiatService.loginUser("testclient")).thenReturn(Calls.response((Void) null));
    when(fiatService.getUserPermission("testclient"))
        .thenReturn(
            Calls.response(
                new UserPermission.View()
                    .setName("testclient")
                    .setAdmin(false)
                    .setAccounts(
                        Set.of(
                            new Account.View()
                                .setName("test-account")
                                .setAuthorizations(ImmutableSet.of(Authorization.WRITE))))
                    .setRoles(
                        Set.of(new Role.View().setName("testRole").setSource(Role.Source.LDAP)))));

    // Create SSL context with client certificate for MTLS
    SSLContext sslContext = createClientSSLContext();

    // Create HTTP client with client certificate
    HttpClient client =
        HttpClient.newBuilder()
            .sslContext(sslContext)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Access the MTLS port with client certificate - should authenticate via X509
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://localhost:" + mtlsPort + "/beans"))
            .GET()
            .build();

    //    System.out.println("Making MTLS request to: https://localhost:" + mtlsPort + "/beans");

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    //    System.out.println("MTLS response status: " + response.statusCode());
    //    System.out.println("MTLS response body length: " + response.body().length());

    // Should successfully authenticate with X509 certificate
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("beans");

    // Verify the authenticated user by accessing /auth/user endpoint
    HttpRequest userRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("https://localhost:" + mtlsPort + "/auth/user"))
            .GET()
            .build();

    HttpResponse<String> userResponse =
        client.send(userRequest, HttpResponse.BodyHandlers.ofString());

    //    System.out.println("User info response: " + userResponse.body());

    assertThat(userResponse.statusCode()).isEqualTo(200);
    assertThat(userResponse.body()).contains("username\":\"testclient\"");
  }

  @Test
  void fullOAuth2AuthenticationFlowUsingBrowserWorks() throws Exception {
    // Configure OAuth2 client in Keycloak
    createOAuth2ClientInKeycloak(mainPort);

    // Mock Fiat responses for user authorization
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

    // Use HtmlUnit to simulate a browser navigating through the OAuth2 flow
    // HtmlUnitDriver with JavaScript enabled (true parameter)
    HtmlUnitDriver driver = new HtmlUnitDriver(true);
    // Accept self-signed SSL certificates
    driver.getWebClient().getOptions().setUseInsecureSSL(true);
    // Increase redirect limit and enable better logging
    driver.getWebClient().getOptions().setMaxInMemory(0);
    driver.getWebClient().getOptions().setPrintContentOnFailingStatusCode(false);

    // Try to access a protected endpoint via HTTPS (main port is HTTPS without client certs)
    String targetUrl = "https://localhost:" + mainPort + "/beans";
    driver.get(targetUrl);

    // Debug: print current URL and page source
    // System.out.println("Initial URL after request: " + driver.getCurrentUrl());
    // System.out.println("Page title: " + driver.getTitle());

    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

    // Wait for Keycloak login page to load
    try {
      wait.until(ExpectedConditions.presenceOfElementLocated(By.id("username")));
    } catch (Exception e) {
      // If we can't find the username field, print the page source for debugging
      System.err.println("Failed to find username field. Current URL: " + driver.getCurrentUrl());
      System.err.println("Page source: " + driver.getPageSource());
      throw e;
    }

    // Fill in login credentials
    driver.findElement(By.id("username")).sendKeys(TEST_USER);
    driver.findElement(By.id("password")).sendKeys(TEST_PASSWORD);

    // System.out.println("About to click login button");
    // System.out.println("Current URL before click: " + driver.getCurrentUrl());

    // Click login and let HtmlUnit handle redirects
    try {
      driver.findElement(By.id("kc-login")).click();
      // System.out.println("After click, current URL: " + driver.getCurrentUrl());
    } catch (Exception e) {
      System.err.println("Exception during click: " + e.getMessage());
      System.err.println("Current URL after exception: " + driver.getCurrentUrl());
      System.err.println("Page source: " + driver.getPageSource());
      throw e;
    }

    // Wait for redirect back to Gate after successful authentication
    wait.until(ExpectedConditions.urlContains("localhost:" + mainPort));

    // Verify we're back at the protected endpoint
    assertThat(driver.getCurrentUrl()).contains("localhost:" + mainPort + "/beans");
    assertThat(driver.getPageSource()).contains("beans");

    // Verify the user is authenticated by accessing the /auth/user endpoint
    driver.navigate().to("https://localhost:" + mainPort + "/auth/user");
    assertThat(driver.getPageSource()).contains("email\":\"" + TEST_EMAIL + "\"");
  }

  private static void generateCertificates() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048, new SecureRandom());

    // Generate CA certificate
    KeyPair caKeyPair = keyPairGenerator.generateKeyPair();
    X509Certificate caCert = generateCACertificate(caKeyPair, "CN=Test CA");

    // Generate server certificate signed by CA
    KeyPair serverKeyPair = keyPairGenerator.generateKeyPair();
    X509Certificate serverCert =
        generateSignedCertificate(serverKeyPair, caKeyPair, caCert, "CN=localhost");

    // Generate client certificate signed by CA
    KeyPair clientKeyPair = keyPairGenerator.generateKeyPair();
    X509Certificate clientCert =
        generateSignedCertificate(clientKeyPair, caKeyPair, caCert, "CN=testclient");

    // Create server keystore (server cert + private key)
    serverKeyStore = new File(tempDir, "server-keystore.jks");
    KeyStore serverKS = KeyStore.getInstance("JKS");
    serverKS.load(null, null);
    serverKS.setKeyEntry(
        "server",
        serverKeyPair.getPrivate(),
        keystorePassword.toCharArray(),
        new Certificate[] {serverCert, caCert});
    try (FileOutputStream fos = new FileOutputStream(serverKeyStore)) {
      serverKS.store(fos, keystorePassword.toCharArray());
    }

    // Create server truststore (CA cert)
    serverTrustStore = new File(tempDir, "server-truststore.jks");
    KeyStore serverTS = KeyStore.getInstance("JKS");
    serverTS.load(null, null);
    serverTS.setCertificateEntry("ca", caCert);
    try (FileOutputStream fos = new FileOutputStream(serverTrustStore)) {
      serverTS.store(fos, keystorePassword.toCharArray());
    }

    // Create client keystore (client cert + private key)
    clientKeyStore = new File(tempDir, "client-keystore.jks");
    KeyStore clientKS = KeyStore.getInstance("JKS");
    clientKS.load(null, null);
    clientKS.setKeyEntry(
        "client",
        clientKeyPair.getPrivate(),
        keystorePassword.toCharArray(),
        new Certificate[] {clientCert, caCert});
    try (FileOutputStream fos = new FileOutputStream(clientKeyStore)) {
      clientKS.store(fos, keystorePassword.toCharArray());
    }

    // Create client truststore (CA cert + server cert)
    clientTrustStore = new File(tempDir, "client-truststore.jks");
    KeyStore clientTS = KeyStore.getInstance("JKS");
    clientTS.load(null, null);
    clientTS.setCertificateEntry("ca", caCert);
    clientTS.setCertificateEntry("server", serverCert);
    try (FileOutputStream fos = new FileOutputStream(clientTrustStore)) {
      clientTS.store(fos, keystorePassword.toCharArray());
    }
  }

  private static X509Certificate generateCACertificate(KeyPair keyPair, String dn)
      throws Exception {
    Instant now = Instant.now();
    Date notBefore = Date.from(now);
    Date notAfter = Date.from(now.plus(Duration.ofDays(365)));

    X500Name issuer = new X500Name(dn);
    X500Name subject = new X500Name(dn);

    JcaX509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            issuer,
            java.math.BigInteger.valueOf(System.currentTimeMillis()),
            notBefore,
            notAfter,
            subject,
            keyPair.getPublic());

    // Mark as CA certificate
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

    ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
    X509CertificateHolder certHolder = certBuilder.build(signer);

    return new JcaX509CertificateConverter().getCertificate(certHolder);
  }

  private static X509Certificate generateSignedCertificate(
      KeyPair subjectKeyPair, KeyPair caKeyPair, X509Certificate caCert, String dn)
      throws Exception {
    Instant now = Instant.now();
    Date notBefore = Date.from(now);
    Date notAfter = Date.from(now.plus(Duration.ofDays(365)));

    X500Name issuer = new X500Name(caCert.getSubjectX500Principal().getName());
    X500Name subject = new X500Name(dn);

    JcaX509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            issuer,
            java.math.BigInteger.valueOf(System.currentTimeMillis()),
            notBefore,
            notAfter,
            subject,
            subjectKeyPair.getPublic());

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256WithRSA").build(caKeyPair.getPrivate());
    X509CertificateHolder certHolder = certBuilder.build(signer);

    return new JcaX509CertificateConverter().getCertificate(certHolder);
  }

  private SSLContext createClientSSLContext() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(Files.newInputStream(clientKeyStore.toPath()), keystorePassword.toCharArray());

    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, keystorePassword.toCharArray());

    KeyStore trustStore = KeyStore.getInstance("JKS");
    trustStore.load(
        Files.newInputStream(clientTrustStore.toPath()), keystorePassword.toCharArray());

    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(
        keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

    return sslContext;
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

    // Create realm
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

    // Create user with email and other attributes for OAuth2
    String userJson =
        """
        {
          "username": "%s",
          "enabled": true,
          "email": "%s",
          "emailVerified": true,
          "firstName": "Test",
          "lastName": "User",
          "credentials": [{"type": "password", "value": "%s", "temporary": false}]
        }
        """
            .formatted(TEST_USER, TEST_EMAIL, TEST_PASSWORD);

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

  private static void createOAuth2ClientInKeycloak(int port)
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

    String clientJson =
        """
        {
         "clientId":"%s",
         "enabled":true,
         "protocol":"openid-connect",
         "publicClient":false,
         "secret":"%s",
         "redirectUris":["https://localhost:%s/login/oauth2/code/keycloak"],
         "webOrigins":["+"],
         "standardFlowEnabled":true,
         "directAccessGrantsEnabled":true,
         "defaultClientScopes":["profile", "email", "openid"],
         "optionalClientScopes":[]
        }
        """
            .formatted(CLIENT_ID, CLIENT_SECRET, port);

    HttpRequest createClientRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + REALM_NAME + "/clients"))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(clientJson))
            .build();

    assertThat(client.send(createClientRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isIn(List.of(201, 409)); // 409 if already created
  }

  private static String extractAccessToken(String json) {
    int start = json.indexOf("\"access_token\":\"") + 16;
    int end = json.indexOf("\"", start);
    return json.substring(start, end);
  }
}
