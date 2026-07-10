/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.aws.security.AWSProxy;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.apache.ProxyConfiguration;

/**
 * Unit tests for proxy configuration translation in {@link AwsSdkV2ClientSupplier}.
 *
 * <p>Tests verify that {@link AWSProxy} settings are correctly mapped to a v2 {@link
 * ProxyConfiguration}.
 */
class AwsSdkV2ClientSupplierProxyTest {

  @Test
  void buildProxyConfiguration_httpProxy() {
    AWSProxy proxy = new AWSProxy("proxy.example.com", "3128", null, null, "HTTP");

    ProxyConfiguration config = AwsSdkV2ClientSupplier.buildProxyConfiguration(proxy);

    assertThat(config.host()).isEqualTo("proxy.example.com");
    assertThat(config.port()).isEqualTo(3128);
    assertThat(config.scheme()).isEqualTo("http");
    assertThat(config.username()).isNull();
    assertThat(config.password()).isNull();
  }

  @Test
  void buildProxyConfiguration_httpsProxy() {
    AWSProxy proxy = new AWSProxy("secure-proxy.example.com", "8443", null, null, "HTTPS");

    ProxyConfiguration config = AwsSdkV2ClientSupplier.buildProxyConfiguration(proxy);

    assertThat(config.host()).isEqualTo("secure-proxy.example.com");
    assertThat(config.port()).isEqualTo(8443);
    assertThat(config.scheme()).isEqualTo("https");
  }

  @Test
  void buildProxyConfiguration_withCredentials() {
    AWSProxy proxy = new AWSProxy("proxy.corp.net", "8080", "admin", "s3cret", "HTTP");

    ProxyConfiguration config = AwsSdkV2ClientSupplier.buildProxyConfiguration(proxy);

    assertThat(config.host()).isEqualTo("proxy.corp.net");
    assertThat(config.port()).isEqualTo(8080);
    assertThat(config.username()).isEqualTo("admin");
    assertThat(config.password()).isEqualTo("s3cret");
  }

  @Test
  void buildProxyConfiguration_nullProtocol_defaultsToHttp() {
    AWSProxy proxy = new AWSProxy("proxy.local", "9090", null, null, null);

    ProxyConfiguration config = AwsSdkV2ClientSupplier.buildProxyConfiguration(proxy);

    assertThat(config.host()).isEqualTo("proxy.local");
    assertThat(config.port()).isEqualTo(9090);
    assertThat(config.scheme()).isEqualTo("http");
  }
}
