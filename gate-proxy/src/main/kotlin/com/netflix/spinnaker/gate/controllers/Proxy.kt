/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.api.extension.ProxyConfig
import com.squareup.okhttp.OkHttpClient
import org.slf4j.LoggerFactory
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal class Proxy(val config: ProxyConfig) {
  companion object {
    val logger = LoggerFactory.getLogger(ProxyConfig::class.java)
  }

  var okHttpClient = OkHttpClient()

  fun init() {
    with(config) {
      okHttpClient.setConnectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
      okHttpClient.setReadTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
      okHttpClient.setWriteTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)

      if (skipHostnameVerification) {
        okHttpClient = okHttpClient.setHostnameVerifier({ hostname, _ ->
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

        okHttpClient = okHttpClient.setSslSocketFactory(sslContext.socketFactory)
      }
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
