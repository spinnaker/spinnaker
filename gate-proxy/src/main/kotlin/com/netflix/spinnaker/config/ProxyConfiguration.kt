/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config

import com.squareup.okhttp.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Configuration
@EnableConfigurationProperties(
  ProxyConfigurationProperties::class
)
open class ProxyConfiguration

@ConfigurationProperties
data class ProxyConfigurationProperties(var proxies: List<ProxyConfig> = mutableListOf()) {

  companion object {
    val logger = LoggerFactory.getLogger(ProxyConfig::class.java)
  }

  @PostConstruct
  fun postConstruct() {
    for (proxy in proxies) {
      try {
        // initialize the `okHttpClient` for each proxy
        proxy.init()
      } catch (e: Exception) {
        logger.error("Failed to initialize proxy (id: ${proxy.id})", e)
      }
    }
  }
}

data class ProxyConfig(
  var id: String? = null,
  var uri: String? = null,
  var skipHostnameVerification: Boolean = false,
  var keyStore: String? = null,
  var keyStoreType: String = KeyStore.getDefaultType(),
  var keyStorePassword: String? = null,
  var keyStorePasswordFile: String? = null,
  var trustStore: String? = null,
  var trustStoreType: String = KeyStore.getDefaultType(),
  var trustStorePassword: String? = null,
  var trustStorePasswordFile: String? = null,
  var methods: List<String> = mutableListOf(),
  var connectTimeoutMs: Long = 30_000,
  var readTimeoutMs: Long = 59_000,
  var writeTimeoutMs: Long = 30_000
) {

  companion object {
    val logger = LoggerFactory.getLogger(ProxyConfig::class.java)
  }

  var okHttpClient = OkHttpClient()

  fun init() {
    okHttpClient.setConnectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
    okHttpClient.setReadTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
    okHttpClient.setWriteTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)

    if (skipHostnameVerification) {
      this.okHttpClient = okHttpClient.setHostnameVerifier({ hostname, _ ->
        logger.warn("Skipping hostname verification on request to $hostname (id: $id)")
        true
      })
    }

    if (!keyStore.isNullOrEmpty()) {
      val keyStorePassword = if (!keyStorePassword.isNullOrEmpty()) {
        keyStorePassword
      } else if (!keyStorePasswordFile.isNullOrEmpty()) {
        File(keyStorePasswordFile).readText()
      } else {
        throw IllegalStateException("No `keyStorePassword` or `keyStorePasswordFile` specified (id: $id)")
      }

      val kStore = KeyStore.getInstance(keyStoreType)

      File(this.keyStore).inputStream().use {
        kStore.load(it, keyStorePassword!!.toCharArray())
      }

      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
      kmf.init(kStore, keyStorePassword!!.toCharArray())

      val keyManagers = kmf.keyManagers
      var trustManagers: Array<TrustManager>? = null

      if (!trustStore.isNullOrEmpty()) {
        if (trustStore.equals("*")) {
          trustManagers = arrayOf(TrustAllTrustManager())
        } else {
          val trustStorePassword = if (!trustStorePassword.isNullOrEmpty()) {
            trustStorePassword
          } else if (!trustStorePasswordFile.isNullOrEmpty()) {
            File(trustStorePasswordFile).readText()
          } else {
            throw IllegalStateException("No `trustStorePassword` or `trustStorePasswordFile` specified (id: $id)")
          }

          val tStore = KeyStore.getInstance(trustStoreType)
          File(this.trustStore).inputStream().use {
            tStore.load(it, trustStorePassword!!.toCharArray())
          }

          val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
          tmf.init(tStore)

          trustManagers = tmf.trustManagers
        }
      }

      val sslContext = SSLContext.getInstance("TLS")
      sslContext.init(keyManagers, trustManagers, null)

      this.okHttpClient = okHttpClient.setSslSocketFactory(sslContext.socketFactory)
    }
  }
}

class TrustAllTrustManager : X509TrustManager {
  override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {
    // do nothing
  }
  override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
    // do nothing
  }

  override fun getAcceptedIssuers() = null
}
