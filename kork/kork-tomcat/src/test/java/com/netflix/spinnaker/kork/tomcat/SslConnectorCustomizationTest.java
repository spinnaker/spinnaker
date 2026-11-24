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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.kork.tomcat.x509.BlocklistingSSLImplementation;
import com.netflix.spinnaker.kork.tomcat.x509.SslExtensionConfigurationProperties;
import java.util.List;
import java.util.Set;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.junit.jupiter.api.Test;

class SslConnectorCustomizationTest {

  private final TomcatConfigurationProperties properties = new TomcatConfigurationProperties();
  private final SslExtensionConfigurationProperties sslProperties =
      new SslExtensionConfigurationProperties();
  private final DefaultTomcatConnectorCustomizer customizer =
      new DefaultTomcatConnectorCustomizer(properties, sslProperties);

  @Test
  void appliesBlocklistingAndTlsSettingsToTomcat11Protocol() {
    Connector connector = new Connector();
    AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
    protocol.setSSLEnabled(true);
    SSLHostConfig sslHostConfig = new SSLHostConfig();
    connector.addSslHostConfig(sslHostConfig);
    properties.setCipherSuites(List.of("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"));
    properties.setTlsVersions(List.of("TLSv1.2"));
    sslProperties.setCrlFile("blocklist:/etc/spinnaker/client-certificates");

    customizer.customize(connector);

    assertEquals(
        BlocklistingSSLImplementation.class.getName(), protocol.getSslImplementationName());
    assertTrue(sslHostConfig.getHonorCipherOrder());
    assertEquals("ECDHE-RSA-AES128-GCM-SHA256", sslHostConfig.getCiphers());
    assertEquals(Set.of("TLSv1.2"), sslHostConfig.getProtocols());
    assertEquals(sslProperties.getCrlFile(), sslHostConfig.getCertificateRevocationListFile());
  }

  @Test
  void leavesNonSslConnectorUnchanged() {
    Connector connector = new Connector();
    AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();

    customizer.customize(connector);

    assertNull(protocol.getSslImplementationName());
    assertEquals(0, connector.findSslHostConfigs().length);
  }

  @Test
  void rejectsSslConnectorWithoutHostConfig() {
    Connector connector = new Connector();
    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setSSLEnabled(true);

    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> customizer.customize(connector));

    assertEquals("Ssl configs: found 0, expected 1.", exception.getMessage());
  }

  @Test
  void rejectsSslConnectorWithMultipleHostConfigs() {
    Connector connector = new Connector();
    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setSSLEnabled(true);
    connector.addSslHostConfig(new SSLHostConfig());
    SSLHostConfig additionalHost = new SSLHostConfig();
    additionalHost.setHostName("additional.example.com");
    connector.addSslHostConfig(additionalHost);

    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> customizer.customize(connector));

    assertEquals("Ssl configs: found 2, expected 1.", exception.getMessage());
  }
}
