/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.haproxy.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.FrontendApi;
import com.netflix.spinnaker.config.HaProxyConfigurationProperties;
import org.junit.jupiter.api.Test;

class HaProxyNamedAccountCredentialsTest {

  private static HaProxyConfigurationProperties.HaProxyManagedAccount account() {
    HaProxyConfigurationProperties.HaProxyManagedAccount account =
        new HaProxyConfigurationProperties.HaProxyManagedAccount();
    account.setName("homelab");
    account.setServer("haproxy.example.com");
    account.setUserName("admin");
    account.setPassword("secret");
    return account;
  }

  @Test
  void buildsCredentialsWithDefaults() {
    HaProxyNamedAccountCredentials credentials = new HaProxyNamedAccountCredentials(account());

    assertThat(credentials.getName()).isEqualTo("homelab");
    assertThat(credentials.getCloudProvider()).isEqualTo("haproxy");
    assertThat(credentials.getRegion()).isEqualTo("default");
    assertThat(credentials.getCredentials()).isNotNull();
  }

  @Test
  void createsGeneratedApiServices() {
    HaProxyNamedAccountCredentials credentials = new HaProxyNamedAccountCredentials(account());

    assertThat(credentials.getApi(FrontendApi.class)).isNotNull();
  }

  @Test
  void insecureHttpsAccountsBuild() {
    HaProxyConfigurationProperties.HaProxyManagedAccount account = account();
    account.setScheme("https");
    account.setInsecure(true);
    account.setRegion("homelab-dc1");

    HaProxyNamedAccountCredentials credentials = new HaProxyNamedAccountCredentials(account);

    assertThat(credentials.getRegion()).isEqualTo("homelab-dc1");
    assertThat(credentials.getCredentials()).isNotNull();
  }
}
