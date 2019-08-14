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
package com.netflix.spinnaker.okhttp;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "ok-http-client")
public class OkHttpClientConfigurationProperties {
  /** Provide backwards compatibility for 'okHttpClient.connectTimoutMs' */
  private long connectTimeoutMs = 15000;

  private long readTimeoutMs = 20000;
  private boolean propagateSpinnakerHeaders = true;

  private File keyStore;
  private String keyStoreType = "PKCS12";
  private String keyStorePassword = "changeit";

  private File trustStore;
  private String trustStoreType = "PKCS12";
  private String trustStorePassword = "changeit";

  private String secureRandomInstanceType = "NativePRNGNonBlocking";

  private List<String> tlsVersions = Arrays.asList("TLSv1.2", "TLSv1.1");

  /**
   * Defaults from https://wiki.mozilla.org/Security/Server_Side_TLS#Modern_compatibility with some
   * extra ciphers (non SHA384/256) to support TLSv1.1 and some non EC ciphers
   */
  private List<String> cipherSuites =
      Arrays.asList(
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
          "TLS_DHE_RSA_WITH_AES_128_CBC_SHA");

  @NestedConfigurationProperty
  private final ConnectionPoolProperties connectionPool = new ConnectionPoolProperties();

  private boolean retryOnConnectionFailure = true;

  @Data
  public static class ConnectionPoolProperties {
    private int maxIdleConnections = 5;
    private int keepAliveDurationMs = 30000;
  }
}
