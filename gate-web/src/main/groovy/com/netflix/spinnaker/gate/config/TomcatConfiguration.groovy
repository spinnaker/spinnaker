package com.netflix.spinnaker.gate.config

import groovy.util.logging.Slf4j
import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.Http11NioProtocol
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory
import org.springframework.boot.context.embedded.Ssl
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@Slf4j
@ConditionalOnExpression('${server.ssl.enabled:false}')
class TomcatConfiguration {
  @Value('${default.legacyServerPort:7101}')
  int legacyServerPort

  @Value('${default.apiPort:-1}')
  int apiPort

  @Value('${server.ssl.keyStore}')
  String keyStore

  @Value('${server.ssl.keyStorePassword}')
  String keyStorePassword

  @Value('${server.ssl.keyStoreType}')
  String keyStoreType

  @Value('${server.ssl.trustStore}')
  String trustStore

  @Value('${server.ssl.trustStorePassword}')
  String trustStorePassword

  @Value('${server.ssl.trustStoreType}')
  String trustStoreType

  /**
   * Setup multiple connectors:
   * - an https connector requiring client auth that will service API requests
   * - an http connector that will service legacy non-https requests
   */
  @Bean
  public EmbeddedServletContainerFactory servletContainer() {
    TomcatEmbeddedServletContainerFactory tomcat = new TomcatEmbeddedServletContainerFactory()

    if (legacyServerPort > 0) {
      def httpConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol")
      httpConnector.setScheme("http")
      httpConnector.setPort(legacyServerPort)
      tomcat.addAdditionalTomcatConnectors(httpConnector)
    }

    if (apiPort > 0) {
      def apiConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol")
      apiConnector.setScheme("https")
      apiConnector.setPort(apiPort)

      tomcat.configureSsl(apiConnector.getProtocolHandler() as Http11NioProtocol, new Ssl(
        keyStore: keyStore,
        keyStorePassword: keyStorePassword,
        keyStoreType: keyStoreType,
        trustStore: trustStore,
        trustStorePassword: trustStorePassword,
        trustStoreType: trustStoreType,
        clientAuth: Ssl.ClientAuth.NEED
      ))
      tomcat.addAdditionalTomcatConnectors(apiConnector)
    }

    return tomcat
  }
}
