package com.netflix.spinnaker.tomcat

import com.netflix.spinnaker.config.TomcatConfigurationProperties
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.tomcat.x509.BlacklistingSSLImplementation
import com.netflix.spinnaker.tomcat.x509.SslExtensionConfigurationProperties
import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.AbstractHttp11JsseProtocol
import org.apache.coyote.http11.AbstractHttp11Protocol
import org.apache.tomcat.util.net.SSLHostConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.Ssl

class TomcatContainerCustomizerUtil {

  private final Logger log = LoggerFactory.getLogger(getClass())

  private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties
  private final SslExtensionConfigurationProperties sslExtensionConfigurationProperties
  private final TomcatConfigurationProperties tomcatConfigurationProperties

  TomcatContainerCustomizerUtil(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
                                SslExtensionConfigurationProperties sslExtensionConfigurationProperties,
                                TomcatConfigurationProperties tomcatConfigurationProperties) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties
    this.sslExtensionConfigurationProperties = sslExtensionConfigurationProperties
    this.tomcatConfigurationProperties = tomcatConfigurationProperties
  }

  Ssl copySslConfigurationWithClientAuth(TomcatServletWebServerFactory tomcat) {
    def ssl = new Ssl()
    tomcat.ssl.properties.each { k, v ->
      try {
        ssl."${k}" = v
      } catch (ReadOnlyPropertyException ignored) {}
    }
    ssl.clientAuth = Ssl.ClientAuth.NEED
    ssl.setCiphers(okHttpClientConfigurationProperties.cipherSuites as String[])
    return ssl
  }

  void applySSLSettings(Connector connector) {
    def handler = connector.getProtocolHandler()
    if (handler instanceof AbstractHttp11JsseProtocol) {
      if (handler.isSSLEnabled()) {
        def sslConfigs = connector.findSslHostConfigs()
        if (sslConfigs.size() != 1) {
          throw new RuntimeException("Ssl configs: found ${sslConfigs.size()}, expected 1.")
        }
        handler.setSslImplementationName(BlacklistingSSLImplementation.name)
        SSLHostConfig sslHostConfig = sslConfigs.first()
        sslHostConfig.setHonorCipherOrder(true)
        sslHostConfig.ciphers = okHttpClientConfigurationProperties.cipherSuites.join(",")
        sslHostConfig.setProtocols(okHttpClientConfigurationProperties.tlsVersions.join(","))
        sslHostConfig.setCertificateRevocationListFile(sslExtensionConfigurationProperties.getCrlFile())
      }
    }
  }

  void applyRelaxedURIProperties(Connector connector) {
    if (!(tomcatConfigurationProperties.relaxedPathCharacters || tomcatConfigurationProperties.relaxedQueryCharacters)) {
      return
    }

    def protocolHandler = connector.protocolHandler
    if (protocolHandler instanceof AbstractHttp11Protocol) {
      if (tomcatConfigurationProperties.relaxedPathCharacters) {
        protocolHandler.relaxedPathChars = tomcatConfigurationProperties.relaxedPathCharacters
      }
      if (tomcatConfigurationProperties.relaxedQueryCharacters) {
        protocolHandler.relaxedQueryChars = tomcatConfigurationProperties.relaxedQueryCharacters
      }
    } else {
      log.warn("Can't apply relaxedPath/Query config to connector of type $connector.protocolHandlerClassName")
    }
  }
}
