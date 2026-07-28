/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.authz.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiTokenExchangeClientTest {

  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private ApiTokenExchangeProperties propsFor(int port) {
    ApiTokenExchangeProperties p = new ApiTokenExchangeProperties();
    p.setEnabled(true);
    p.setCacheTtl(Duration.ofMinutes(5));
    return p;
  }

  private String urlFor(int port) {
    return "http://localhost:" + port;
  }

  /** A fake (unsigned) JWT carrying only an {@code exp} claim; the client never verifies it. */
  private static String jwtExpiringAt(Instant exp) {
    String header = b64("{\"alg\":\"none\"}");
    String payload = b64("{\"exp\":" + exp.getEpochSecond() + "}");
    return header + "." + payload + ".sig";
  }

  private static String b64(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private int startServer(HttpHandler handler) throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/auth/apiTokens/exchange", handler);
    server.start();
    return server.getAddress().getPort();
  }

  @Test
  @DisplayName("returns the identity token from a 200 response")
  void returnsIdentityToken() throws Exception {
    String jwt = jwtExpiringAt(Instant.now().plusSeconds(300));
    int port =
        startServer(exchange -> respond(exchange, 200, "{\"identityToken\":\"" + jwt + "\"}"));

    ApiTokenExchangeClient client = new ApiTokenExchangeClient(propsFor(port), urlFor(port));
    Optional<String> result = client.exchange("spk_abc");

    assertThat(result).contains(jwt);
  }

  @Test
  @DisplayName("caches the exchanged token, so a second call does not hit Gate")
  void cachesPositiveResult() throws Exception {
    String jwt = jwtExpiringAt(Instant.now().plusSeconds(300));
    AtomicInteger hits = new AtomicInteger();
    int port =
        startServer(
            exchange -> {
              hits.incrementAndGet();
              respond(exchange, 200, "{\"identityToken\":\"" + jwt + "\"}");
            });

    ApiTokenExchangeClient client = new ApiTokenExchangeClient(propsFor(port), urlFor(port));
    assertThat(client.exchange("spk_abc")).contains(jwt);
    assertThat(client.exchange("spk_abc")).contains(jwt);

    assertThat(hits.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("returns empty for a 401 (unknown/expired/rejected token)")
  void emptyOnUnauthorized() throws Exception {
    int port = startServer(exchange -> respond(exchange, 401, "{}"));

    ApiTokenExchangeClient client = new ApiTokenExchangeClient(propsFor(port), urlFor(port));

    assertThat(client.exchange("spk_bad")).isEmpty();
  }

  @Test
  @DisplayName("returns empty when Gate is unreachable")
  void emptyOnConnectionFailure() {
    ApiTokenExchangeProperties p = new ApiTokenExchangeProperties();
    p.setEnabled(true);
    p.setConnectTimeoutMillis(250);
    p.setReadTimeoutMillis(250);

    // Nothing is listening on this port.
    ApiTokenExchangeClient client = new ApiTokenExchangeClient(p, "http://localhost:1");

    assertThat(client.exchange("spk_abc")).isEmpty();
  }

  private static void respond(HttpExchange exchange, int status, String body) {
    try {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
