/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.okhttp

import com.fasterxml.jackson.annotation.JsonAlias
import groovy.transform.AutoClone
import groovy.transform.Canonical
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.core.annotation.AliasFor

@AutoClone
@Canonical
@ConfigurationProperties(prefix="ok-http-client")
class OkHttpClientConfigurationProperties {
  long connectTimeoutMs = 15000
  long readTimeoutMs = 20000

  boolean propagateSpinnakerHeaders = true

  File keyStore
  String keyStoreType = 'PKCS12'
  String keyStorePassword = 'changeit'

  File trustStore
  String trustStoreType = 'PKCS12'
  String trustStorePassword = 'changeit'

  String secureRandomInstanceType = "NativePRNGNonBlocking"

  List<String> tlsVersions = ["TLSv1.2", "TLSv1.1"]
  //Defaults from https://wiki.mozilla.org/Security/Server_Side_TLS#Modern_compatibility
  // with some extra ciphers (non SHA384/256) to support TLSv1.1 and some non EC ciphers
  List<String> cipherSuites = [
    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA"
  ]

  /**
   * Provide backwards compatibility for 'okHttpClient.connectTimoutMs'
   */
  void setConnectTimoutMs(long connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  @Canonical
  static class ConnectionPoolProperties {
    int maxIdleConnections = 5
    int keepAliveDurationMs = 30000
  }

  @NestedConfigurationProperty
  final ConnectionPoolProperties connectionPool = new ConnectionPoolProperties()

  boolean retryOnConnectionFailure = true

}
