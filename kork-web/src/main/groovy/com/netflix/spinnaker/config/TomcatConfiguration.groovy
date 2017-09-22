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
import com.netflix.spinnaker.tomcat.TomcatContainerCustomizerUtil
import com.netflix.spinnaker.tomcat.x509.SslExtensionConfigurationProperties
import groovy.util.logging.Slf4j
import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.Http11NioProtocol
import org.springframework.boot.actuate.endpoint.ResolvedEnvironmentEndpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Slf4j
@Configuration
@EnableConfigurationProperties([ResolvedEnvironmentEndpoint, SslExtensionConfigurationProperties, TomcatConfigurationProperties])
class TomcatConfiguration {

  @Bean
  TomcatContainerCustomizerUtil tomcatContainerCustomizerUtil(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
                                                              SslExtensionConfigurationProperties sslExtensionConfigurationProperties) {
    return new TomcatContainerCustomizerUtil(okHttpClientConfigurationProperties, sslExtensionConfigurationProperties)
  }
  /**
   * Setup multiple connectors:
   * - an https connector requiring client auth that will service API requests
   * - an http connector that will service legacy non-https requests
   */
  @Bean
  @ConditionalOnExpression('${server.ssl.enabled:false}')
  EmbeddedServletContainerCustomizer containerCustomizer(TomcatContainerCustomizerUtil tomcatContainerCustomizerUtil,
                                                         TomcatConfigurationProperties tomcatConfigurationProperties) throws Exception {
    System.setProperty("jdk.tls.rejectClientInitiatedRenegotiation", "true")
    System.setProperty("jdk.tls.ephemeralDHKeySize", "2048")

    return { ConfigurableEmbeddedServletContainer container ->
      TomcatEmbeddedServletContainerFactory tomcat = (TomcatEmbeddedServletContainerFactory) container
      //this will only handle the case where SSL is enabled on the main tomcat connector
      tomcat.addConnectorCustomizers(new TomcatConnectorCustomizer() {
        @Override
        void customize(Connector connector) {
          tomcatContainerCustomizerUtil.applySSLSettings(connector)
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

        def ssl = tomcatContainerCustomizerUtil.copySslConfigurationWithClientAuth(tomcat)
        Http11NioProtocol handler = apiConnector.getProtocolHandler() as Http11NioProtocol
        tomcat.configureSsl(handler, ssl)
        tomcatContainerCustomizerUtil.applySSLSettings(apiConnector)
        tomcat.addAdditionalTomcatConnectors(apiConnector)
      }
    } as EmbeddedServletContainerCustomizer
  }
}
