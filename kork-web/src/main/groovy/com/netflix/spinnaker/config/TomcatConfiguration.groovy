/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.config

import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.tomcat.x509.BlacklistingSSLImplementation
import com.netflix.spinnaker.tomcat.x509.BlacklistingX509TrustManager
import com.netflix.spinnaker.tomcat.x509.SslExtensionConfigurationProperties
import groovy.util.logging.Slf4j
import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.AbstractHttp11JsseProtocol
import org.apache.coyote.http11.Http11NioProtocol
import org.apache.tomcat.util.net.SSLHostConfig
import org.springframework.boot.actuate.endpoint.ResolvedEnvironmentEndpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer
import org.springframework.boot.context.embedded.Ssl
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Slf4j
@Configuration
@EnableConfigurationProperties([ResolvedEnvironmentEndpoint, SslExtensionConfigurationProperties, TomcatConfigurationProperties])
class TomcatConfiguration {

  /**
   * Setup multiple connectors:
   * - an https connector requiring client auth that will service API requests
   * - an http connector that will service legacy non-https requests
   */
  @Bean
  @ConditionalOnExpression('${server.ssl.enabled:false}')
  EmbeddedServletContainerCustomizer containerCustomizer(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
                                                         TomcatConfigurationProperties tomcatConfigurationProperties,
                                                         SslExtensionConfigurationProperties sslExtensionConfigurationProperties) throws Exception {
    System.setProperty("jdk.tls.rejectClientInitiatedRenegotiation", "true")
    System.setProperty("jdk.tls.ephemeralDHKeySize", "2048")

    return { ConfigurableEmbeddedServletContainer container ->
      TomcatEmbeddedServletContainerFactory tomcat = (TomcatEmbeddedServletContainerFactory) container
      //this will only handle the case where SSL is enabled on the main tomcat connector
      tomcat.addConnectorCustomizers(new TomcatConnectorCustomizer() {
        @Override
        void customize(Connector connector) {
          def handler = connector.getProtocolHandler()
          if (handler instanceof AbstractHttp11JsseProtocol) {
            if (handler.isSSLEnabled()) {
              def sslConfigs = connector.findSslHostConfigs()
              if (sslConfigs.size() != 1) {
                throw new RuntimeException("Ssl configs: found ${sslConfigs.size()}, expected 1.")
              }
              handler.setSslImplementationName(BlacklistingSSLImplementation.name)
              SSLHostConfig sslHostConfig = sslConfigs.first()
              sslHostConfig.setHonorCipherOrder("true")
              sslHostConfig.ciphers = okHttpClientConfigurationProperties.cipherSuites.join(",")
              sslHostConfig.setProtocols(okHttpClientConfigurationProperties.tlsVersions.join(","))
              sslHostConfig.setCertificateRevocationListFile(sslExtensionConfigurationProperties.getCrlFile())
            }
          }
        }
      })

      if (tomcatConfigurationProperties.getLegacyServerPort()> 0) {
        log.info("Creating legacy connector on port ${tomcatConfigurationProperties.getLegacyServerPort()}")
        def httpConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol")
        httpConnector.setScheme("http")
        httpConnector.setPort(tomcatConfigurationProperties.getLegacyServerPort())
        tomcat.addAdditionalTomcatConnectors(httpConnector)
      }

      if (tomcatConfigurationProperties.getApiPort() > 0) {
        log.info("Creating api connector on port ${tomcatConfigurationProperties.getApiPort()}")
        def apiConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol")
        apiConnector.setScheme("https")
        apiConnector.setPort(tomcatConfigurationProperties.getApiPort())

        def ssl = new Ssl()
        tomcat.ssl.properties.each { k, v ->
          try {
            ssl."${k}" = v
          } catch (ReadOnlyPropertyException ignored) {}
        }
        ssl.clientAuth = Ssl.ClientAuth.NEED
        ssl.setCiphers(okHttpClientConfigurationProperties.cipherSuites as String[])

        Http11NioProtocol handler = apiConnector.getProtocolHandler() as Http11NioProtocol
        handler.setSslImplementationName(BlacklistingSSLImplementation.name)
        tomcat.configureSsl(handler, ssl)
        def sslConfigs = apiConnector.findSslHostConfigs()
        if (sslConfigs.size() != 1) {
          throw new RuntimeException("Ssl configs: found ${sslConfigs.size()}, expected 1.")
        }
        SSLHostConfig sslHostConfig = sslConfigs.first()
        sslHostConfig.setHonorCipherOrder("true")
        sslHostConfig.ciphers = okHttpClientConfigurationProperties.cipherSuites.join(",")
        sslHostConfig.setProtocols(okHttpClientConfigurationProperties.tlsVersions.join(","))
        sslHostConfig.setCertificateRevocationListFile(sslExtensionConfigurationProperties.getCrlFile())

        tomcat.addAdditionalTomcatConnectors(apiConnector)
      }
    } as EmbeddedServletContainerCustomizer
  }
}
