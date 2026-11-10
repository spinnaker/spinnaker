/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class MaxLocaleCountWebTest {

  @ParameterizedTest
  @ValueSource(ints = {12, -1, -2})
  void profileOverrideAllowsMoreThanTenLocales(int limit) throws Exception {
    try (ConfigurableApplicationContext context =
            new SpringApplicationBuilder(TestApplication.class)
                .properties("server.port=0", "default.max-locale-count=" + limit)
                .run();
        HttpClient client = HttpClient.newHttpClient()) {
      int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
      HttpResponse<String> response = request(client, port, MaxLocaleCountTest.firstLanguages(12));

      assertEquals(200, response.statusCode());
      assertEquals("en-US", response.body());

      if (limit >= 0) {
        assertEquals(
            500, request(client, port, MaxLocaleCountTest.firstLanguages(13)).statusCode());
      } else {
        assertEquals(200, request(client, port, MaxLocaleCountTest.LANGUAGES).statusCode());
      }
    }
  }

  private static HttpResponse<String> request(HttpClient client, int port, String languages)
      throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/locale"))
            .header("Accept-Language", languages)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import(LocaleController.class)
  static class TestApplication {}

  @RestController
  static class LocaleController {
    @GetMapping("/locale")
    String locale(Locale locale) {
      return locale.toLanguageTag();
    }
  }
}
