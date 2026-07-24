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
package com.netflix.spinnaker.clouddriver.haproxy.caching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HaProxyCacheKeysTest {

  @Test
  void keysFollowTheProviderTypeAccountRegionNameLayout() {
    assertThat(HaProxyCacheKeys.frontend("homelab", "dc1", "web-main"))
        .isEqualTo("haproxy;FRONTEND;homelab;dc1;web-main");
    assertThat(HaProxyCacheKeys.backend("homelab", "dc1", "web-main-v001"))
        .isEqualTo("haproxy;BACKEND;homelab;dc1;web-main-v001");
    assertThat(HaProxyCacheKeys.application("web")).isEqualTo("haproxy;APPLICATION;web");
    assertThat(HaProxyCacheKeys.cluster("homelab", "web-main"))
        .isEqualTo("haproxy;CLUSTER;homelab;web-main");
  }

  @Test
  void keyPartsAreParsedBack() {
    String key = HaProxyCacheKeys.frontend("homelab", "dc1", "web-main");

    assertThat(HaProxyCacheKeys.getAccount(key)).isEqualTo("homelab");
    assertThat(HaProxyCacheKeys.getRegion(key)).isEqualTo("dc1");
    assertThat(HaProxyCacheKeys.getName(key)).isEqualTo("web-main");
  }

  @Test
  void globsMatchTypeAndAccountScopes() {
    assertThat(HaProxyCacheKeys.glob(HaProxyResourceType.FRONTEND.name(), "homelab"))
        .isEqualTo("haproxy;FRONTEND;homelab;*");
    assertThat(HaProxyCacheKeys.globAll(HaProxyResourceType.BACKEND.name()))
        .isEqualTo("haproxy;BACKEND;*");
    assertThat(HaProxyCacheKeys.globAllApplications()).isEqualTo("haproxy;APPLICATION;*");
  }
}
