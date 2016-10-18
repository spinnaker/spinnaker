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

import com.netflix.spinnaker.tomcat.x509.BlacklistingSSLImplementation
import groovy.util.logging.Slf4j
import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.AbstractHttp11JsseProtocol
import org.apache.coyote.http11.Http11NioProtocol
import org.springframework.beans.factory.annotation.Value
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
@EnableConfigurationProperties(ResolvedEnvironmentEndpoint)
class TomcatConfiguration {
  @Value('${default.legacyServerPort:-1}')
  int legacyServerPort

  @Value('${default.apiPort:-1}')
  int apiPort

  //TODO(cfieber) remove this when https://github.com/spring-projects/spring-boot/issues/6171 is implemented
  // the default value of null equates to no CRL file in the connector config so we don't have to null-check it
  // below
  @Value('${server.ssl.crlFile:#{null}}')
  String crlFile

  /**
   * Setup multiple connectors:
   * - an https connector requiring client auth that will service API requests
   * - an http connector that will service legacy non-https requests
   */
  @Bean
  @ConditionalOnExpression('${server.ssl.enabled:false}')
  EmbeddedServletContainerCustomizer containerCustomizer() throws Exception {
    return { ConfigurableEmbeddedServletContainer container ->
      TomcatEmbeddedServletContainerFactory tomcat = (TomcatEmbeddedServletContainerFactory) container
      //this will only handle the case where SSL is enabled on the main tomcat connector
      tomcat.addConnectorCustomizers(new TomcatConnectorCustomizer() {
        @Override
        void customize(Connector connector) {
          def handler = connector.getProtocolHandler()
          if (handler instanceof AbstractHttp11JsseProtocol) {
            if (handler.isSSLEnabled()) {
              handler.setSslImplementationName(BlacklistingSSLImplementation.name)
              handler.setCrlFile(crlFile)
            }
          }
        }
      })

      if (legacyServerPort > 0) {
        log.info("Creating legacy connector on port ${legacyServerPort}")
        def httpConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol")
        httpConnector.setScheme("http")
        httpConnector.setPort(legacyServerPort)
        tomcat.addAdditionalTomcatConnectors(httpConnector)
      }

      if (apiPort > 0) {
        log.info("Creating api connector on port ${apiPort}")
        def apiConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol")
        apiConnector.setScheme("https")
        apiConnector.setPort(apiPort)

        def ssl = new Ssl()
        tomcat.ssl.properties.each { k, v ->
          try {
            ssl."${k}" = v
          } catch (ReadOnlyPropertyException ignored) {}
        }
        ssl.clientAuth = Ssl.ClientAuth.NEED

        Http11NioProtocol handler = apiConnector.getProtocolHandler() as Http11NioProtocol
        handler.setCrlFile(crlFile)
        handler.setSslImplementationName(BlacklistingSSLImplementation.name)

        tomcat.configureSsl(handler, ssl)
        tomcat.addAdditionalTomcatConnectors(apiConnector)
      }
    } as EmbeddedServletContainerCustomizer
  }
}
