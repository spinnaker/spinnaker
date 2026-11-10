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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.crypto.test.CertificateIdentity;
import com.netflix.spinnaker.kork.tomcat.x509.SslExtensionConfigurationProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

class MaxLocaleCountTest {

  static final String LANGUAGES =
      "en-US,en-GB,fr-FR,de-DE,es-ES,it-IT,pt-BR,ja-JP,ko-KR,zh-CN,nl-NL,sv-SE,da-DK,fi-FI,pl-PL";

  @TempDir Path directory;

  @Test
  void unsetPropertyPreservesTomcatDefaultAndExistingOverrides() {
    DefaultTomcatConnectorCustomizer customizer =
        new DefaultTomcatConnectorCustomizer(
            new TomcatConfigurationProperties(), new SslExtensionConfigurationProperties());
    Connector connector = new Connector();
    int defaultLimit = connector.getMaxLocaleCount();

    customizer.customize(connector);

    assertEquals(defaultLimit, connector.getMaxLocaleCount());
    assertThrows(IllegalArgumentException.class, () -> parseLocales(connector, LANGUAGES));

    connector.setMaxLocaleCount(42);
    customizer.customize(connector);

    assertEquals(42, connector.getMaxLocaleCount());
  }

  @SuppressWarnings("unchecked") // The existing containerCustomizer bean has a raw return type.
  @ParameterizedTest
  @ValueSource(ints = {0, 12, -1, -2})
  void bindsAndAppliesLimitToMainLegacyAndX509Connectors(int limit) throws IOException {
    Path keyStore = directory.resolve("server.p12");
    CertificateIdentity.generateSelfSigned().saveAsPKCS12(keyStore, "test-password".toCharArray());
    Ssl ssl = new Ssl();
    ssl.setKeyStore(keyStore.toUri().toString());
    ssl.setKeyStorePassword("test-password");
    new ApplicationContextRunner()
        .withUserConfiguration(TomcatConfiguration.class)
        .withPropertyValues(
            "server.ssl.enabled=true",
            "default.legacy-server-port=8081",
            "default.api-port=8443",
            "default.maxLocaleCount=" + limit)
        .run(
            context -> {
              TestTomcatFactory factory = new TestTomcatFactory();
              factory.setSsl(ssl);
              factory.setCompression(new Compression());
              context
                  .getBean("containerCustomizer", WebServerFactoryCustomizer.class)
                  .customize(factory);

              Connector main = new Connector();
              factory.customizeMainConnector(main);
              verifyLimit(main, limit);

              assertEquals(2, factory.getAdditionalConnectors().size());
              for (Connector connector : factory.getAdditionalConnectors()) {
                verifyLimit(connector, limit);
              }
            });
  }

  private static class TestTomcatFactory extends TomcatServletWebServerFactory {
    void customizeMainConnector(Connector connector) {
      super.customizeConnector(connector);
    }
  }

  private static void verifyLimit(Connector connector, int limit) {
    assertEquals(limit, connector.getMaxLocaleCount());
    if (limit < 0) {
      // Exercise Tomcat's actual parser, including a negative value other than -1.
      assertEquals(15, parseLocales(connector, LANGUAGES));
    } else {
      if (limit > 0) {
        assertEquals(limit, parseLocales(connector, firstLanguages(limit)));
      }
      assertThrows(
          IllegalArgumentException.class, () -> parseLocales(connector, firstLanguages(limit + 1)));
    }
  }

  static String firstLanguages(int count) {
    return String.join(",", Arrays.copyOf(LANGUAGES.split(","), count));
  }

  private static int parseLocales(Connector connector, String languages) {
    org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
    coyoteRequest.getMimeHeaders().addValue("Accept-Language").setString(languages);
    Request request = new Request(connector, coyoteRequest);
    return Collections.list(request.getLocales()).size();
  }
}
