package com.netflix.spinnaker.gate.config

import groovy.util.logging.Slf4j
import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.Http11NioProtocol
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer
import org.springframework.boot.context.embedded.Ssl
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@Slf4j
class TomcatConfiguration {
  @Value('${default.legacyServerPort:7101}')
  int legacyServerPort

  @Value('${default.apiPort:-1}')
  int apiPort

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

        tomcat.configureSsl(apiConnector.getProtocolHandler() as Http11NioProtocol, ssl)
        tomcat.addAdditionalTomcatConnectors(apiConnector)
      }
    } as EmbeddedServletContainerCustomizer
  }
}
