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

package com.netflix.spinnaker.okhttp;

import com.netflix.spinnaker.kork.crypto.CipherSuites;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "ok-http-client")
public class OkHttpClientConfigurationProperties {
  private long connectTimeoutMs = 5000;
  private long readTimeoutMs = 120000;
  private int maxRequests = 100;
  private int maxRequestsPerHost = 100;

  /**
   * Whether to propagate headers to outgoing HTTP requests. If false, no headers are propagated.
   * Not X-SPINNAKER-*, not additionalHeaders.
   */
  private boolean propagateSpinnakerHeaders = true;

  /**
   * Determines whether to skip the correction of partial encoding done by Retrofit2. Refer
   * https://github.com/spinnaker/spinnaker/issues/7021 for more details
   */
  private boolean skipRetrofit2EncodeCorrection = false;

  /**
   * Headers to propagate from the MDC, in addition to the X-SPINNAKER-* headers. Each element whose
   * the value in the MDC is non-empty is included in outgoing HTTP requests.
   */
  private List<String> additionalHeaders = new ArrayList<>();

  /**
   * true to omit the root execution id header from outgoing HTTP requests. false to include it (if
   * it's present in the MDC).
   */
  private boolean skipRootExecutionIdHeader = false;

  private File keyStore;
  private String keyStoreType = "PKCS12";
  private String keyStorePassword = "changeit";

  private File trustStore;
  private String trustStoreType = "PKCS12";
  private String trustStorePassword = "changeit";

  private String secureRandomInstanceType = "NativePRNGNonBlocking";
  // TLS1.1 isn't supported in newer JVMs... do NOT try to add back - it's also insecure
  private List<String> tlsVersions = new ArrayList<>(List.of("TLSv1.3", "TLSv1.2"));
  private List<String> cipherSuites = CipherSuites.getRecommendedCiphers();

  /** Provide backwards compatibility for 'okHttpClient.connectTimoutMs' */
  public void setConnectTimoutMs(long connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  @Data
  public static class ConnectionPoolProperties {
    private int maxIdleConnections = 15;
    private int keepAliveDurationMs = 30000;
  }

  @NestedConfigurationProperty
  private ConnectionPoolProperties connectionPool = new ConnectionPoolProperties();

  private boolean retryOnConnectionFailure = true;

  /**
   * Configuration properties for supporting refreshable keys. When this is enabled, the configured
   * keystore file will be periodically reloaded if the file changes.
   */
  @Data
  public static class RefreshableKeys {
    private boolean enabled = false;
    private Duration refreshPeriod = Duration.ofMinutes(30);
  }

  @NestedConfigurationProperty private RefreshableKeys refreshableKeys = new RefreshableKeys();
}
