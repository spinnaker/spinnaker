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

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.model.Image;
import com.netflix.spinnaker.clouddriver.model.ImageProvider;
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxImage;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProxmoxImageProvider implements ImageProvider {

  private final Cache cacheView;

  @Autowired
  public ProxmoxImageProvider(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @Override
  public String getCloudProvider() {
    return ProxmoxProvider.ID;
  }

  @Override
  public Optional<Image> getImageById(String imageId) {
    CacheData data = cacheView.get(ProxmoxResourceType.IMAGE.name(), imageId);
    if (data == null) return Optional.empty();
    return Optional.of(buildImage(imageId, data));
  }

  private ProxmoxImage buildImage(String key, CacheData data) {
    var attrs = data.getAttributes();
    String name = (String) attrs.get("name");
    String node = ProxmoxCacheKeys.getNode(key);
    String account = ProxmoxCacheKeys.getAccount(key);
    String vmIdStr = ProxmoxCacheKeys.getId(key);
    Integer vmId = vmIdStr != null ? Integer.parseInt(vmIdStr) : null;
    String vmType = (String) attrs.get("vmType");

    return ProxmoxImage.builder()
        .id(key)
        .name(name)
        .region(node)
        .account(account)
        .vmId(vmId)
        .vmType(vmType)
        .build();
  }
}
