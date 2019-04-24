package com.netflix.spinnaker.tomcat

import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.tomcat.x509.BlacklistingSSLImplementation
import com.netflix.spinnaker.tomcat.x509.SslExtensionConfigurationProperties
import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.AbstractHttp11JsseProtocol
import org.apache.tomcat.util.net.SSLHostConfig
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.Ssl

class TomcatContainerCustomizerUtil {

  private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties
  private final SslExtensionConfigurationProperties sslExtensionConfigurationProperties

  TomcatContainerCustomizerUtil(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
                                SslExtensionConfigurationProperties sslExtensionConfigurationProperties) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties
    this.sslExtensionConfigurationProperties = sslExtensionConfigurationProperties
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
}
