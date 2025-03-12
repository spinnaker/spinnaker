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

import com.netflix.spinnaker.kork.crypto.CipherSuites
import groovy.transform.AutoClone
import groovy.transform.Canonical
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@AutoClone
@Canonical
@ConfigurationProperties(prefix="ok-http-client")
class OkHttpClientConfigurationProperties {
  long connectTimeoutMs = 5000
  long readTimeoutMs = 120000
  int maxRequests = 100
  int maxRequestsPerHost = 100

  boolean propagateSpinnakerHeaders = true

  File keyStore
  String keyStoreType = 'PKCS12'
  String keyStorePassword = 'changeit'

  File trustStore
  String trustStoreType = 'PKCS12'
  String trustStorePassword = 'changeit'

  String secureRandomInstanceType = "NativePRNGNonBlocking"
  // TLS1.1 isn't supported in newer JVMs... do NOT try to add back - it's also insecure
  List<String> tlsVersions = ["TLSv1.3", "TLSv1.2"]
  List<String> cipherSuites = CipherSuites.recommendedCiphers

  /**
   * Provide backwards compatibility for 'okHttpClient.connectTimoutMs'
   */
  void setConnectTimoutMs(long connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  @Canonical
  static class ConnectionPoolProperties {
    int maxIdleConnections = 15
    int keepAliveDurationMs = 30000
  }

  @NestedConfigurationProperty
  final ConnectionPoolProperties connectionPool = new ConnectionPoolProperties()

  boolean retryOnConnectionFailure = true

  /**
   * Configuration properties for supporting refreshable keys. When this is enabled, the configured
   * keystore file will be periodically reloaded if the file changes.
   */
  @Canonical
  static class RefreshableKeys {
    boolean enabled = false
    Duration refreshPeriod = Duration.ofMinutes(30)
  }

  @NestedConfigurationProperty
  final RefreshableKeys refreshableKeys = new RefreshableKeys()

}
