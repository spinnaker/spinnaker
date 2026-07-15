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

package com.netflix.spinnaker.clouddriver.artifacts.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Arrays;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaseHttpArtifactCredentialsTest {

  private MockWebServer mockServer;
  private MockWebServer maliciousServer;
  private TestArtifactCredentials credentials;

  @BeforeEach
  void setUp() throws IOException {
    mockServer = new MockWebServer();
    mockServer.start();

    maliciousServer = new MockWebServer();
    maliciousServer.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    mockServer.shutdown();
    maliciousServer.shutdown();
  }

  @Test
  void testSSRFPreventionViaRedirect() throws Exception {
    // Set up URL restrictions that allow the malicious server but block localhost
    HttpUrlRestrictions restrictions =
        HttpUrlRestrictions.builder()
            .allowedHostnamesRegex(".*")
            .rejectLocalhost(true)
            .rejectLinkLocal(true)
            .allowedSchemes(Arrays.asList("http", "https"))
            .build();

    TestArtifactAccount account = new TestArtifactAccount();
    account.setUrlRestrictions(restrictions);

    OkHttpClient client = new OkHttpClient();
    credentials = new TestArtifactCredentials(client, account);

    // Mock the malicious server to redirect to localhost (simulating SSRF attack)
    String localhostTarget = "http://127.0.0.1:" + mockServer.getPort() + "/internal/secret-data";
    maliciousServer.enqueue(
        new MockResponse().setResponseCode(302).setHeader("Location", localhostTarget));

    // Mock the internal endpoint response
    mockServer.enqueue(new MockResponse().setBody("SECRET_API_KEY=12345").setResponseCode(200));

    String attackUrl = maliciousServer.url("/redirect").toString();

    // The attack should be blocked with IllegalArgumentException during redirect validation
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              try {
                credentials.fetchUrl(attackUrl);
              } catch (IOException e) {
                // Unwrap IllegalArgumentException from IOException cause chain
                Throwable cause = e.getCause();
                while (cause != null) {
                  if (cause instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) cause;
                  }
                  cause = cause.getCause();
                }
                throw e;
              }
            });

    assertTrue(
        exception.getMessage().contains("localhost")
            || exception.getMessage().contains("127.0.0.1"),
        "Expected error about localhost/loopback being blocked, got: " + exception.getMessage());

    // Verify that the internal endpoint was NEVER called
    assertEquals(
        0,
        mockServer.getRequestCount(),
        "Internal endpoint should not have been reached due to redirect validation");
  }

  @Test
  void testLegitimateRedirectsAreFollowed() throws Exception {
    HttpUrlRestrictions restrictions =
        HttpUrlRestrictions.builder()
            .allowedHostnamesRegex(".*")
            .rejectLocalhost(false) // Allow localhost for this test
            .allowedSchemes(Arrays.asList("http", "https"))
            .build();

    TestArtifactAccount account = new TestArtifactAccount();
    account.setUrlRestrictions(restrictions);

    OkHttpClient client = new OkHttpClient();
    credentials = new TestArtifactCredentials(client, account);

    // Set up a legitimate redirect chain
    String finalUrl = mockServer.url("/final").toString();
    maliciousServer.enqueue(
        new MockResponse().setResponseCode(302).setHeader("Location", finalUrl));
    mockServer.enqueue(new MockResponse().setBody("legitimate content").setResponseCode(200));

    String initialUrl = maliciousServer.url("/start").toString();
    String content = credentials.fetchUrl(initialUrl).string();

    assertEquals("legitimate content", content);
    assertEquals(1, maliciousServer.getRequestCount());
    assertEquals(1, mockServer.getRequestCount());
  }

  @Test
  void testRedirectLoopPrevention() throws Exception {
    HttpUrlRestrictions restrictions =
        HttpUrlRestrictions.builder()
            .allowedHostnamesRegex(".*")
            .rejectLocalhost(false)
            .allowedSchemes(Arrays.asList("http", "https"))
            .build();

    TestArtifactAccount account = new TestArtifactAccount();
    account.setUrlRestrictions(restrictions);

    credentials = new TestArtifactCredentials(new OkHttpClient(), account);

    // Create redirect loop
    String loopUrl1 = mockServer.url("/loop1").toString();
    String loopUrl2 = mockServer.url("/loop2").toString();

    for (int i = 0; i < 20; i++) {
      mockServer.enqueue(
          new MockResponse()
              .setResponseCode(302)
              .setHeader("Location", i % 2 == 0 ? loopUrl2 : loopUrl1));
    }

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              credentials.fetchUrl(loopUrl1);
            });

    assertTrue(
        exception.getMessage().contains("Too many redirects"),
        "Expected 'Too many redirects' error, got: " + exception.getMessage());
  }

  @Test
  void testRelativeRedirectResolution() throws Exception {
    HttpUrlRestrictions restrictions =
        HttpUrlRestrictions.builder()
            .allowedHostnamesRegex(".*")
            .rejectLocalhost(false)
            .allowedSchemes(Arrays.asList("http", "https"))
            .build();

    TestArtifactAccount account = new TestArtifactAccount();
    account.setUrlRestrictions(restrictions);

    credentials = new TestArtifactCredentials(new OkHttpClient(), account);

    // Relative redirect
    mockServer.enqueue(
        new MockResponse().setResponseCode(302).setHeader("Location", "/final-path"));
    mockServer.enqueue(new MockResponse().setBody("relative redirect worked").setResponseCode(200));

    String content = credentials.fetchUrl(mockServer.url("/start").toString()).string();

    assertEquals("relative redirect worked", content);
    assertEquals(2, mockServer.getRequestCount());

    RecordedRequest firstReq = mockServer.takeRequest();
    RecordedRequest secondReq = mockServer.takeRequest();

    assertEquals("/start", firstReq.getPath());
    assertEquals("/final-path", secondReq.getPath());
  }

  // Test implementation classes
  static class TestArtifactAccount extends UserInputValidatedArtifactAccount {
    private HttpUrlRestrictions mutableRestrictions;

    TestArtifactAccount() {
      super("test-account", null);
    }

    @Override
    public HttpUrlRestrictions getUrlRestrictions() {
      return mutableRestrictions;
    }

    public void setUrlRestrictions(HttpUrlRestrictions restrictions) {
      this.mutableRestrictions = restrictions;
    }
  }

  static class TestArtifactCredentials extends BaseHttpArtifactCredentials<TestArtifactAccount> {
    protected TestArtifactCredentials(OkHttpClient okHttpClient, TestArtifactAccount account) {
      super(okHttpClient, account);
    }
  }
}
