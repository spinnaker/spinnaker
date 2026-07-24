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
package com.netflix.spinnaker.clouddriver.proxmox.provider.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.model.Image;
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxImage;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxmoxImageProviderTest {

  private static final String ACCOUNT = "my-proxmox";
  private static final String NODE = "pve01";
  private static final int VMID = 9000;

  @Mock private Cache cacheView;

  private ProxmoxImageProvider provider;

  @BeforeEach
  void setUp() {
    provider = new ProxmoxImageProvider(cacheView);
  }

  @Test
  void getCloudProviderReturnsProxmoxId() {
    assertThat(provider.getCloudProvider()).isEqualTo(ProxmoxProvider.ID);
  }

  @Test
  void getImageByIdReturnsPresentWhenCacheHit() {
    String key = ProxmoxCacheKeys.image(ACCOUNT, NODE, VMID);
    Map<String, Object> attrs =
        Map.of("name", "ubuntu-template", "vmType", "qemu", "region", NODE, "account", ACCOUNT);
    when(cacheView.get(ProxmoxResourceType.IMAGE.name(), key))
        .thenReturn(new DefaultCacheData(key, attrs, Collections.emptyMap()));

    Optional<Image> result = provider.getImageById(key);

    assertThat(result).isPresent();
    ProxmoxImage image = (ProxmoxImage) result.get();
    assertThat(image.getId()).isEqualTo(key);
    assertThat(image.getName()).isEqualTo("ubuntu-template");
    assertThat(image.getRegion()).isEqualTo(NODE);
    assertThat(image.getAccount()).isEqualTo(ACCOUNT);
    assertThat(image.getVmId()).isEqualTo(VMID);
    assertThat(image.getVmType()).isEqualTo("qemu");
  }

  @Test
  void getImageByIdReturnsEmptyWhenCacheMiss() {
    String key = ProxmoxCacheKeys.image(ACCOUNT, NODE, VMID);
    when(cacheView.get(ProxmoxResourceType.IMAGE.name(), key)).thenReturn(null);

    Optional<Image> result = provider.getImageById(key);

    assertThat(result).isEmpty();
  }

  @Test
  void getImageByIdHandlesLxcVmType() {
    String key = ProxmoxCacheKeys.image(ACCOUNT, NODE, 9001);
    Map<String, Object> attrs =
        Map.of("name", "debian-template", "vmType", "lxc", "region", NODE, "account", ACCOUNT);
    when(cacheView.get(ProxmoxResourceType.IMAGE.name(), key))
        .thenReturn(new DefaultCacheData(key, attrs, Collections.emptyMap()));

    Optional<Image> result = provider.getImageById(key);

    assertThat(result).isPresent();
    assertThat(((ProxmoxImage) result.get()).getVmType()).isEqualTo("lxc");
  }
}
