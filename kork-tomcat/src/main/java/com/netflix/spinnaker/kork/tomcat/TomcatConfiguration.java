/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.tomcat;

import com.netflix.spinnaker.kork.tomcat.x509.SslExtensionConfigurationProperties;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  TomcatConfigurationProperties.class,
  SslExtensionConfigurationProperties.class
})
class TomcatConfiguration {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Bean
  TomcatConnectorCustomizer defaultTomcatConnectorCustomizer(
      TomcatConfigurationProperties tomcatConfigurationProperties,
      SslExtensionConfigurationProperties sslExtensionConfigurationProperties) {
    return new DefaultTomcatConnectorCustomizer(
        tomcatConfigurationProperties, sslExtensionConfigurationProperties);
  }

  /**
   * Setup multiple connectors: - an https connector requiring client auth that will service API
   * requests - an http connector that will service legacy non-https requests
   */
  @Bean
  @ConditionalOnExpression("${server.ssl.enabled:false}")
  WebServerFactoryCustomizer containerCustomizer(
      DefaultTomcatConnectorCustomizer defaultTomcatConnectorCustomizer,
      TomcatConfigurationProperties tomcatConfigurationProperties) {
    System.setProperty("jdk.tls.rejectClientInitiatedRenegotiation", "true");
    System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");

    return new WebServerFactoryCustomizer() {
      @Override
      public void customize(WebServerFactory factory) {
        TomcatServletWebServerFactory tomcat = (TomcatServletWebServerFactory) factory;

        // This will only handle the case where SSL is enabled on the main Tomcat connector
        tomcat.addConnectorCustomizers(defaultTomcatConnectorCustomizer);

        if (tomcatConfigurationProperties.getLegacyServerPort() > 0) {
          log.info(
              "Creating legacy connector on port {}",
              tomcatConfigurationProperties.getLegacyServerPort());
          Connector httpConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
          httpConnector.setScheme("http");
          httpConnector.setPort(tomcatConfigurationProperties.getLegacyServerPort());

          applyCompressionSettings(httpConnector, tomcat);
          tomcat.addAdditionalTomcatConnectors(httpConnector);
        }

        if (tomcatConfigurationProperties.getApiPort() > 0) {
          log.info("Creating api connector on port {}", tomcatConfigurationProperties.getApiPort());
          Connector apiConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
          apiConnector.setScheme("https");
          apiConnector.setSecure(true);
          apiConnector.setPort(tomcatConfigurationProperties.getApiPort());

          applyCompressionSettings(apiConnector, tomcat);

          Ssl ssl = defaultTomcatConnectorCustomizer.copySslConfigurationWithClientAuth(tomcat);
          CustomizableTomcatServletWebServerFactory newFactory =
              new CustomizableTomcatServletWebServerFactory();
          BeanUtils.copyProperties(tomcat, newFactory);
          newFactory.setPort(tomcatConfigurationProperties.getApiPort());
          newFactory.setSsl(ssl);
          newFactory.customizeSslConnector(apiConnector);
          defaultTomcatConnectorCustomizer.customize(apiConnector);
          tomcat.addAdditionalTomcatConnectors(apiConnector);
        }
      }
    };
  }

  /**
   * Apply compression setting to the protocol of the given connector Spring will configure the main
   * connector with compression settings from `server:` settings in YAML, but for secondary
   * connectors we have to do that ourselves. Use the config from the default connector
   */
  private static void applyCompressionSettings(
      Connector connector, TomcatServletWebServerFactory tomcat) {
    Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();

    if (tomcat.getCompression().getEnabled()) {
      protocol.setCompression("on");
      protocol.setCompressibleMimeType(String.join(",", tomcat.getCompression().getMimeTypes()));
      protocol.setCompressionMinSize((int) tomcat.getCompression().getMinResponseSize().toBytes());
    } else {
      protocol.setCompression("off");
    }
  }

  private static class CustomizableTomcatServletWebServerFactory
      extends TomcatServletWebServerFactory {
    void customizeSslConnector(Connector connector) {
      super.customizeConnector(connector);
    }
  }
}
