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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.url.UrlRestrictions;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
    UrlRestrictions restrictions =
        UrlRestrictionsProperties.builder()
            .allowedHostnamesRegex(".*")
            .rejectLocalhost(true)
            .rejectLinkLocal(true)
            .allowedSchemes(Arrays.asList("http", "https"))
            .build()
            .toUrlRestrictions();

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
    UrlRestrictions restrictions =
        UrlRestrictionsProperties.builder()
            .allowedHostnamesRegex(".*")
            .rejectLocalhost(false) // Allow localhost for this test
            .allowedSchemes(Arrays.asList("http", "https"))
            .build()
            .toUrlRestrictions();

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
    UrlRestrictions restrictions =
        UrlRestrictionsProperties.builder()
            .allowedHostnamesRegex(".*")
            .rejectLocalhost(false)
            .allowedSchemes(Arrays.asList("http", "https"))
            .build()
            .toUrlRestrictions();

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
    UrlRestrictions restrictions =
        UrlRestrictionsProperties.builder()
            .allowedHostnamesRegex(".*")
            .rejectLocalhost(false)
            .allowedSchemes(Arrays.asList("http", "https"))
            .build()
            .toUrlRestrictions();

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

  @Test
  void failedDownloadNamesTheRateLimit() {
    TestArtifactAccount account = new TestArtifactAccount();
    credentials = new TestArtifactCredentials(new OkHttpClient(), account);
    long reset = Instant.now().getEpochSecond() + 411;
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(403)
            .setHeader("X-RateLimit-Limit", "5000")
            .setHeader("X-RateLimit-Remaining", "0")
            .setHeader("X-RateLimit-Resource", "core")
            .setHeader("X-RateLimit-Reset", String.valueOf(reset))
            .setBody("API rate limit exceeded for user ID 1"));

    IOException e =
        assertThrows(
            IOException.class, () -> credentials.fetchUrl(mockServer.url("/file").toString()));

    assertTrue(
        e.getMessage().startsWith("Received 403 status code from "), "message: " + e.getMessage());
    assertTrue(
        e.getMessage().contains("(rate limit: remaining=0, limit=5000, resource=core, resets in "),
        "message: " + e.getMessage());
    assertFalse(e.getMessage().contains("user ID"), "body leaked: " + e.getMessage());
  }

  @Test
  void failedDownloadWithoutRateLimitHeadersKeepsTheMessage() {
    TestArtifactAccount account = new TestArtifactAccount();
    credentials = new TestArtifactCredentials(new OkHttpClient(), account);
    mockServer.enqueue(new MockResponse().setResponseCode(404));

    IOException e =
        assertThrows(
            IOException.class, () -> credentials.fetchUrl(mockServer.url("/file").toString()));

    assertEquals("Received 404 status code from " + mockServer.url("/").host(), e.getMessage());
  }

  @Test
  void rateLimitDetailIgnoresMalformedHeaders() {
    Headers headers =
        new Headers.Builder()
            .add("X-RateLimit-Remaining", "-1")
            .add("X-RateLimit-Resource", "Core; evil")
            .add("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT")
            .build();
    assertEquals("", BaseHttpArtifactCredentials.rateLimitDetail(headers, Instant.now()));

    Headers retry = new Headers.Builder().add("Retry-After", "60").build();
    assertEquals(
        "rate limit: retry after 60s",
        BaseHttpArtifactCredentials.rateLimitDetail(retry, Instant.now()));
  }

  @Test
  void credentialsAreLoggedOnceAcrossRedirects() throws Exception {
    TestArtifactAccount account = new TestArtifactAccount();
    credentials =
        new TestArtifactCredentials(
            new OkHttpClient(), account, Headers.of("Authorization", "token abc"));

    // Same host first, then a cross-host hop (127.0.0.1) that is sent without credentials.
    String otherHost =
        maliciousServer.url("/final").newBuilder().host("127.0.0.1").build().toString();
    mockServer.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/next"));
    mockServer.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", otherHost));
    maliciousServer.enqueue(new MockResponse().setBody("content"));

    List<String> logs =
        credentialLogs(
            () ->
                credentials.fetchUrl(
                    mockServer.url("/start").newBuilder().host("localhost").build()));

    assertEquals(List.of("Loaded credentials for artifact account test-account"), logs);
    assertEquals("token abc", mockServer.takeRequest().getHeader("Authorization"));
    assertNull(maliciousServer.takeRequest().getHeader("Authorization"));
  }

  @Test
  void downloadWithoutCredentialsIsLogged() throws Exception {
    TestArtifactAccount account = new TestArtifactAccount();
    credentials = new TestArtifactCredentials(new OkHttpClient(), account);
    mockServer.enqueue(new MockResponse().setBody("content"));

    List<String> logs = credentialLogs(() -> credentials.fetchUrl(mockServer.url("/file")));

    assertEquals(List.of("No credentials included for artifact account test-account"), logs);
  }

  @Test
  void subclassesDecideWhichHeaderCarriesCredentials() throws Exception {
    TestArtifactAccount account = new TestArtifactAccount();
    credentials =
        new TestArtifactCredentials(
            new OkHttpClient(), account, Headers.of("Private-Token", "abc")) {
          @Override
          protected boolean sendsCredentials(Headers headers) {
            return headers.get("Private-Token") != null;
          }
        };
    mockServer.enqueue(new MockResponse().setBody("content"));

    List<String> logs = credentialLogs(() -> credentials.fetchUrl(mockServer.url("/file")));

    assertEquals(List.of("Loaded credentials for artifact account test-account"), logs);
  }

  interface Fetch {
    void run() throws IOException;
  }

  /** Runs the fetch and returns the credentials log lines it wrote. */
  private static List<String> credentialLogs(Fetch fetch) throws IOException {
    Logger logger = (Logger) LoggerFactory.getLogger(BaseHttpArtifactCredentials.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      fetch.run();
    } finally {
      logger.detachAppender(appender);
    }
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(m -> m.contains("credentials"))
        .toList();
  }

  static class TestArtifactAccount extends UserInputValidatedArtifactAccount {
    private UrlRestrictions mutableRestrictions;

    TestArtifactAccount() {
      super("test-account", null, new Permissions.Builder());
    }

    @Override
    public UrlRestrictions getUrlRestrictions() {
      return mutableRestrictions;
    }

    public void setUrlRestrictions(UrlRestrictions restrictions) {
      this.mutableRestrictions = restrictions;
    }
  }

  static class TestArtifactCredentials extends BaseHttpArtifactCredentials<TestArtifactAccount> {
    private final Headers headers;

    protected TestArtifactCredentials(OkHttpClient okHttpClient, TestArtifactAccount account) {
      this(okHttpClient, account, new Headers.Builder().build());
    }

    protected TestArtifactCredentials(
        OkHttpClient okHttpClient, TestArtifactAccount account, Headers headers) {
      super(okHttpClient, account);
      this.headers = headers;
    }

    @Override
    protected Headers getHeaders(TestArtifactAccount account) {
      return headers;
    }

    @Override
    public String getName() {
      return getAccount().getName();
    }

    @Override
    public String getType() {
      return "test";
    }

    @Override
    public List<String> getTypes() {
      return List.of();
    }

    @Override
    public InputStream download(Artifact artifact) {
      throw new UnsupportedOperationException();
    }
  }
}
