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
package com.netflix.spinnaker.clouddriver.proxmox.controllers;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves cached Proxmox templates through gate's standard {@code /images/find?provider=proxmox}
 * route, powering template dropdowns in deck.
 */
@RestController
@RequestMapping("/proxmox/images")
public class ProxmoxImageLookupController {

  private final Cache cacheView;

  public ProxmoxImageLookupController(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @GetMapping("/find")
  public List<Map<String, Object>> find(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String account,
      @RequestParam(required = false) String region) {

    String glob =
        account != null
            ? ProxmoxCacheKeys.glob(ProxmoxResourceType.IMAGE.name(), account)
            : ProxmoxCacheKeys.globAll(ProxmoxResourceType.IMAGE.name());
    Collection<String> ids = cacheView.filterIdentifiers(ProxmoxResourceType.IMAGE.name(), glob);

    List<Map<String, Object>> results = new ArrayList<>();
    String query = q == null || q.isBlank() || "*".equals(q) ? null : q.toLowerCase(Locale.ROOT);

    for (CacheData data : cacheView.getAll(ProxmoxResourceType.IMAGE.name(), ids)) {
      Map<String, Object> attrs = data.getAttributes();
      String name = (String) attrs.get("name");
      String node = ProxmoxCacheKeys.getNode(data.getId());
      String imageAccount = ProxmoxCacheKeys.getAccount(data.getId());
      String vmid = ProxmoxCacheKeys.getId(data.getId());

      if (region != null && !region.equals(node)) continue;
      if (query != null
          && (name == null || !name.toLowerCase(Locale.ROOT).contains(query))
          && (vmid == null || !vmid.contains(query))) {
        continue;
      }

      Map<String, Object> image = new LinkedHashMap<>();
      image.put("imageName", name);
      image.put("imageId", vmid);
      image.put("vmid", vmid);
      image.put("region", node);
      image.put("account", imageAccount);
      image.put("vmType", attrs.get("vmType"));
      results.add(image);
    }

    results.sort(
        Comparator.comparing(
            image -> String.valueOf(image.get("imageName")), String.CASE_INSENSITIVE_ORDER));
    return results;
  }
}
