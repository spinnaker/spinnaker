/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Slf4j
abstract public class ManifestBasedModel {
  public String getName() {
    return getManifest().getFullResourceName();
  }

  // Spinnaker namespace hacks
  public String getZone() {
    return getManifest().getNamespace();
  }

  // Spinnaker namespace hacks
  public String getRegion() {
    return getManifest().getNamespace();
  }

  public String getUid() {
    return getManifest().getUid();
  }

  public String getType() {
    return KubernetesCloudProvider.getID();
  }

  public String getCloudProvider() {
    return KubernetesCloudProvider.getID();
  }

  public String getProviderType() {
    return KubernetesCloudProvider.getID();
  }

  public Moniker getMoniker() {
    return NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.getID())
        .withAccount(getAccountName())
        .withResource(KubernetesManifest.class)
        .deriveMoniker(getManifest());
  }

  public String getAccountName() {
    return getKey().getAccount();
  }

  public String getAccount() {
    return getAccountName();
  }

  public Long getCreatedTime() {
    Map<String, String> metadata = (Map<String, String>) getManifest().getOrDefault("metadata", new HashMap<>());
    String timestamp = metadata.get("creationTimestamp");
    try {
      if (StringUtils.isNotEmpty(timestamp)) {
        return (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").parse(timestamp)).getTime();
      }
    } catch (ParseException e) {
      log.warn("Failed to parse timestamp: ", e);
    }
    
    return null;
  }

  public KubernetesKind getKind() {
    return getManifest().getKind();
  }

  abstract protected KubernetesManifest getManifest();
  abstract protected Keys.InfrastructureCacheKey getKey();
}
