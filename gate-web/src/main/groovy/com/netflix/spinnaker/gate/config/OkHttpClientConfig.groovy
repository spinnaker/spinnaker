package com.netflix.spinnaker.gate.config

import com.squareup.okhttp.OkHttpClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.AccessController
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivilegedAction
import java.security.SecureRandom
import java.security.Security
import java.util.concurrent.TimeUnit

@Slf4j
@CompileStatic
@Configuration
@ConfigurationProperties(prefix="okHttpClient")
class OkHttpClientConfig {
  long connectTimoutMs = 15000
  long readTimeoutMs = 20000

  File keyStore
  String keyStoreType = 'PKCS12'
  String keyStorePassword = 'changeit'

  File trustStore
  String trustStoreType = 'PKCS12'
  String trustStorePassword = 'changeit'

  OkHttpClient create() {
    def okHttpClient = new OkHttpClient()
    okHttpClient.setConnectTimeout(connectTimoutMs, TimeUnit.MILLISECONDS)
    okHttpClient.setReadTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)

    if (!keyStore && !trustStore) {
      return okHttpClient
    }

    def sslContext = SSLContext.getInstance('TLS')

    def keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    def ks = KeyStore.getInstance(keyStoreType)
    keyStore.withInputStream {
      ks.load(it as InputStream, keyStorePassword.toCharArray())
    }
    keyManagerFactory.init(ks, keyStorePassword.toCharArray())

    def trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    def ts = KeyStore.getInstance(trustStoreType)
    trustStore.withInputStream {
      ts.load(it as InputStream, trustStorePassword.toCharArray())
    }
    trustManagerFactory.init(ts)

    String strongAlgorithms = AccessController.doPrivileged(
      new PrivilegedAction<String>() {
        @Override
        public String run() {
          return Security.getProperty(
            "securerandom.strongAlgorithms");
        }
      });
    log.info("Available strong algorithms: ${strongAlgorithms}")

    def secureRandom = new SecureRandom()
    try {
      secureRandom = SecureRandom.getInstanceStrong()
    } catch (NoSuchAlgorithmException e) {
      log.error("Unable to fetch strong secure random instance", e)
    }

    sslContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, secureRandom)
    okHttpClient.setSslSocketFactory(sslContext.socketFactory)

    return okHttpClient
  }
}
