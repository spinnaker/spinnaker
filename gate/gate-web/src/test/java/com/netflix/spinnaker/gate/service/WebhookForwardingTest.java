/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Posts webhooks to a running Gate and inspects what Echo receives. Echo verifies git webhook
 * signatures against the headers and the raw body it is sent, so both have to survive the hop
 * through Gate unchanged.
 */
@SpringBootTest(
    classes = {Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    value = "/application-echo.properties",
    properties = "spring.config.location=classpath:gate-test.yml")
class WebhookForwardingTest {

  private static final String SECRET = "s3cret";

  @LocalServerPort int port;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  /** To prevent refreshing the applications cache, which involves calls to clouddriver. */
  @MockitoBean ApplicationService applicationService;

  /** To prevent calls to clouddriver */
  @MockitoBean DefaultProviderLookupService defaultProviderLookupService;

  @RegisterExtension
  static WireMockExtension wmEcho =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension wmClouddriver =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension wmFront50 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    registry.add("services.echo.base-url", wmEcho::baseUrl);
    registry.add("services.clouddriver.base-url", wmClouddriver::baseUrl);
    registry.add("services.front50.base-url", wmFront50::baseUrl);
  }

  @BeforeAll
  static void stubDownstreamServices() {
    wmClouddriver.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
    wmFront50.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
  }

  @BeforeEach
  void resetEcho() {
    wmEcho.resetAll();
    wmEcho.stubFor(
        any(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));
  }

  /**
   * Exercise the signature headers that senders attach to a webhook. Gate used to forward only
   * {@code X-Hub-Signature} and {@code X-Event-Key}, so for example Gitea's {@code
   * X-Gitea-Signature} never reached Echo.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("headersEchoNeeds")
  void forwardsSenderHeadersToEcho(String source, String header, String value) throws Exception {
    send(source, "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8), header, value);

    LoggedRequest received = singleRequestTo(source);
    assertThat(received.getHeader(header)).isEqualTo(value);
  }

  static Stream<Arguments> headersEchoNeeds() {
    return Stream.of(
        Arguments.of("github", "X-Hub-Signature", "sha1=abc"),
        Arguments.of("github", "X-Hub-Signature-256", "sha256=abc"),
        Arguments.of("github", "X-GitHub-Event", "create"),
        Arguments.of("gitea", "X-Gitea-Signature", "abc"),
        Arguments.of("gitea", "X-Gitea-Event", "delete"),
        Arguments.of("gitea", "X-Hub-Signature-256", "sha256=abc"),
        Arguments.of("bitbucket", "X-Event-Key", "repo:push"),
        Arguments.of("bitbucket", "X-Hub-Signature", "sha256=abc"));
  }

  @Test
  void doesNotForwardUnrelatedOrCredentialHeadersToEcho() throws Exception {
    send(
        "gitea",
        "{}".getBytes(StandardCharsets.UTF_8),
        "Authorization",
        "Bearer should-not-leave-gate",
        "Cookie",
        "SESSION=should-not-leave-gate",
        "X-Spinnaker-User",
        "spoofed",
        "X-Gitea-Event",
        "push");

    LoggedRequest received = singleRequestTo("gitea");
    assertThat(received.getHeader("X-Gitea-Event")).isEqualTo("push");
    assertThat(received.containsHeader("Authorization")).isFalse();
    assertThat(received.containsHeader("Cookie")).isFalse();
    assertThat(received.containsHeader("X-Gitlab-Token")).isFalse();
    assertThat(received.getHeader("X-Spinnaker-User")).isNotEqualTo("spoofed");
  }

  /**
   * Echo computes the HMAC over the body it receives, so a body that Gate changed on the way
   * through can never verify. Payloads here are shaped like what the senders produce: Go services
   * (Gitea) HTML-escape {@code < > &}, others pretty print or send numbers in exponent form.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("bodies")
  void forwardsTheBodyByteForByte(String name, String body) throws Exception {
    byte[] original = body.getBytes(StandardCharsets.UTF_8);
    send("github", original, "X-Hub-Signature", "sha1=" + hmacSha1(original));

    LoggedRequest received = singleRequestTo("github");

    assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo(body);
    assertThat(hmacSha1(received.getBody())).isEqualTo(hmacSha1(original));
  }

  static Stream<Arguments> bodies() {
    return Stream.of(
        Arguments.of(
            "compact",
            "{\"ref\":\"refs/heads/main\",\"before\":\"ca73\",\"after\":\"c242\",\"id\":105648914}"),
        Arguments.of("key order is not alphabetical", "{\"z\":1,\"a\":2,\"m\":{\"y\":1,\"b\":2}}"),
        Arguments.of("pretty printed", "{\n  \"ref\": \"refs/heads/main\",\n  \"id\": 1\n}\n"),
        Arguments.of(
            "html escapes (Go encoding/json)", "{\"message\":\"a \\u0026 b \\u003cc\\u003e\"}"),
        Arguments.of("literal html characters", "{\"message\":\"a & b <c>\"}"),
        Arguments.of("non-ascii as utf-8", "{\"author\":\"Zoë 日本語 😀\"}"),
        Arguments.of("non-ascii as escapes", "{\"author\":\"Zo\\u00eb \\ud83d\\ude00\"}"),
        Arguments.of("escaped slash", "{\"url\":\"https:\\/\\/example.com\\/x\"}"),
        Arguments.of("float formatting", "{\"a\":1.0,\"b\":1e5,\"c\":0.10,\"d\":-0}"),
        Arguments.of(
            "numbers beyond double precision",
            "{\"id\":12345678901234567890123,\"f\":0.1000000000000000055511151231257827}"),
        Arguments.of("duplicate keys", "{\"a\":1,\"a\":2}"),
        Arguments.of("null and empty values", "{\"a\":null,\"b\":[],\"c\":{},\"d\":\"\"}"));
  }

  private static String hmacSha1(byte[] body) {
    return HmacUtils.hmacSha1Hex(SECRET.getBytes(StandardCharsets.UTF_8), body);
  }

  /** Sends exactly {@code body}; a client library would re-encode it. */
  private void send(String source, byte[] body, String... headerNamesAndValues)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/webhooks/git/" + source))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    for (int i = 0; i < headerNamesAndValues.length; i += 2) {
      request.header(headerNamesAndValues[i], headerNamesAndValues[i + 1]);
    }
    HttpResponse<String> response =
        httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
  }

  private LoggedRequest singleRequestTo(String source) {
    List<LoggedRequest> requests =
        wmEcho.findAll(postRequestedFor(urlPathEqualTo("/webhooks/git/" + source)));
    assertThat(requests).hasSize(1);
    return requests.get(0);
  }
}
