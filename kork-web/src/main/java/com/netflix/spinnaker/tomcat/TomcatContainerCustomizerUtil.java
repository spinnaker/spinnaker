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

package com.netflix.spinnaker.tomcat;

import com.google.common.base.Joiner;
import com.netflix.spinnaker.config.TomcatConfigurationProperties;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.tomcat.x509.BlacklistingSSLImplementation;
import com.netflix.spinnaker.tomcat.x509.SslExtensionConfigurationProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;

@Slf4j
public class TomcatContainerCustomizerUtil {
  private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties;
  private final SslExtensionConfigurationProperties sslExtensionConfigurationProperties;
  private final TomcatConfigurationProperties tomcatConfigurationProperties;

  public TomcatContainerCustomizerUtil(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
      SslExtensionConfigurationProperties sslExtensionConfigurationProperties,
      TomcatConfigurationProperties tomcatConfigurationProperties) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties;
    this.sslExtensionConfigurationProperties = sslExtensionConfigurationProperties;
    this.tomcatConfigurationProperties = tomcatConfigurationProperties;
  }

  public Ssl copySslConfigurationWithClientAuth(TomcatServletWebServerFactory tomcat) {
    final Ssl ssl = new Ssl();
    ssl.setCiphers(okHttpClientConfigurationProperties.getCipherSuites().toArray(new String[0]));
    ssl.setClientAuth(Ssl.ClientAuth.NEED);
    ssl.setEnabled(tomcat.getSsl().isEnabled());
    ssl.setEnabledProtocols(tomcat.getSsl().getEnabledProtocols());
    ssl.setKeyAlias(tomcat.getSsl().getKeyAlias());
    ssl.setKeyPassword(tomcat.getSsl().getKeyPassword());
    ssl.setKeyStore(tomcat.getSsl().getKeyStore());
    ssl.setKeyStorePassword(tomcat.getSsl().getKeyStorePassword());
    ssl.setKeyStoreProvider(tomcat.getSsl().getKeyStoreProvider());
    ssl.setKeyStoreType(tomcat.getSsl().getKeyStoreType());
    ssl.setProtocol(tomcat.getSsl().getProtocol());
    ssl.setTrustStore(tomcat.getSsl().getTrustStore());
    ssl.setTrustStorePassword(tomcat.getSsl().getTrustStorePassword());
    ssl.setTrustStoreProvider(tomcat.getSsl().getTrustStoreProvider());
    ssl.setTrustStoreType(tomcat.getSsl().getTrustStoreType());
    return ssl;
  }

  public void applySSLSettings(Connector connector) {
    ProtocolHandler handler = connector.getProtocolHandler();
    if (handler instanceof AbstractHttp11JsseProtocol) {
      if (((AbstractHttp11JsseProtocol) handler).isSSLEnabled()) {
        final SSLHostConfig[] sslConfigs = connector.findSslHostConfigs();
        if (sslConfigs.length != 1) {
          throw new RuntimeException("Ssl configs: found " + sslConfigs.length + ", expected 1.");
        }

        ((AbstractHttp11JsseProtocol) handler)
            .setSslImplementationName(BlacklistingSSLImplementation.class.getName());
        SSLHostConfig sslHostConfig = DefaultGroovyMethods.first(sslConfigs);
        sslHostConfig.setHonorCipherOrder(true);
        sslHostConfig.setCiphers(
            Joiner.on(",").join(okHttpClientConfigurationProperties.getCipherSuites()));
        sslHostConfig.setProtocols(
            Joiner.on(",").join(okHttpClientConfigurationProperties.getTlsVersions()));
        sslHostConfig.setCertificateRevocationListFile(
            sslExtensionConfigurationProperties.getCrlFile());
      }
    }
  }

  public void applyRelaxedURIProperties(Connector connector) {
    final boolean relaxedPathCharacters =
        Boolean.valueOf(tomcatConfigurationProperties.getRelaxedPathCharacters());
    final boolean relaxedQueryCharacters =
        Boolean.valueOf(tomcatConfigurationProperties.getRelaxedQueryCharacters());
    if (!(relaxedPathCharacters || relaxedQueryCharacters)) {
      return;
    }

    ProtocolHandler protocolHandler = connector.getProtocolHandler();
    if (protocolHandler instanceof AbstractHttp11Protocol) {
      if (relaxedPathCharacters) {
        ((AbstractHttp11Protocol) protocolHandler)
            .setRelaxedPathChars(tomcatConfigurationProperties.getRelaxedPathCharacters());
      }

      if (relaxedQueryCharacters) {
        ((AbstractHttp11Protocol) protocolHandler)
            .setRelaxedQueryChars(tomcatConfigurationProperties.getRelaxedQueryCharacters());
      }

    } else {
      log.warn(
          "Can't apply relaxedPath/Query config to connector of type "
              + connector.getProtocolHandlerClassName());
    }
  }
}
