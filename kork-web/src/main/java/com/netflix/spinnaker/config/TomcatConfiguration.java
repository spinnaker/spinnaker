/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.config;

import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.tomcat.TomcatContainerCustomizerUtil;
import com.netflix.spinnaker.tomcat.x509.SslExtensionConfigurationProperties;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@Configuration
@ComponentScan(
    basePackages = {"com.netflix.spinnaker.endpoint", "com.netflix.spinnaker.tomcat.x509"})
@EnableConfigurationProperties({
  ResolvedEnvironmentConfigurationProperties.class,
  SslExtensionConfigurationProperties.class,
  TomcatConfigurationProperties.class
})
@EnableScheduling
public class TomcatConfiguration {

  @Bean
  TomcatContainerCustomizerUtil tomcatContainerCustomizerUtil(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
      SslExtensionConfigurationProperties sslExtensionConfigurationProperties,
      TomcatConfigurationProperties tomcatConfigurationProperties) {
    return new TomcatContainerCustomizerUtil(
        okHttpClientConfigurationProperties,
        sslExtensionConfigurationProperties,
        tomcatConfigurationProperties);
  }

  /**
   * Setup multiple connectors: - an https connector requiring client auth that will service API
   * requests - an http connector that will service legacy non-https requests
   */
  @Bean
  @ConditionalOnExpression("${server.ssl.enabled:false}")
  WebServerFactoryCustomizer containerCustomizer(
      TomcatContainerCustomizerUtil tomcatContainerCustomizerUtil,
      TomcatConfigurationProperties tomcatConfigurationProperties)
      throws Exception {
    System.setProperty("jdk.tls.rejectClientInitiatedRenegotiation", "true");
    System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");

    return factory -> {
      TomcatServletWebServerFactory tomcat = (TomcatServletWebServerFactory) factory;

      // This will only handle the case where SSL is enabled on the main Tomcat connector
      tomcat.addConnectorCustomizers(
          (TomcatConnectorCustomizer)
              connector -> {
                tomcatContainerCustomizerUtil.applySSLSettings(connector);
                tomcatContainerCustomizerUtil.applyRelaxedURIProperties(connector);
              });

      if (tomcatConfigurationProperties.getLegacyServerPort() > 0) {
        log.info(
            "Creating legacy connecgtor on port {}",
            tomcatConfigurationProperties.getLegacyServerPort());
        Connector httpConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        httpConnector.setScheme("http");
        httpConnector.setPort(tomcatConfigurationProperties.getLegacyServerPort());
        tomcat.addAdditionalTomcatConnectors(httpConnector);
      }

      if (tomcatConfigurationProperties.getApiPort() > 0) {
        log.info("Creating api connector on port {}", tomcatConfigurationProperties.getApiPort());
        Connector apiConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        apiConnector.setScheme("https");
        apiConnector.setPort(tomcatConfigurationProperties.getApiPort());

        Ssl ssl = tomcatContainerCustomizerUtil.copySslConfigurationWithClientAuth(tomcat);

        // Run for the hills.
        try {
          Class<?> sslCustomizer =
              Class.forName("org.springframework.boot.web.embedded.tomcat.SslConnectorCustomizer");
          Constructor<?> construct =
              sslCustomizer.getDeclaredConstructor(Ssl.class, SslStoreProvider.class);
          construct.setAccessible(true);
          Object customizer = construct.newInstance(ssl, tomcat.getSslStoreProvider());
          sslCustomizer.getMethod("customize", Connector.class).invoke(customizer, apiConnector);
        } catch (ClassNotFoundException
            | InstantiationException
            | InvocationTargetException
            | NoSuchMethodException
            | IllegalAccessException e) {
          throw new SystemException("Failed to customize api connector", e);
        }

        tomcatContainerCustomizerUtil.applySSLSettings(apiConnector);
        tomcatContainerCustomizerUtil.applyRelaxedURIProperties(apiConnector);
        tomcat.addAdditionalTomcatConnectors(apiConnector);
      }
    };
  }
}
