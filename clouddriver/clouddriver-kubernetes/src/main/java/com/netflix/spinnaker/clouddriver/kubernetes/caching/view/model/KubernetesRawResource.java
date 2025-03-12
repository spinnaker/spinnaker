/*
 * Copyright 2020 Coveo, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value
public final class KubernetesRawResource implements KubernetesResource {
  private static final Logger log = LoggerFactory.getLogger(KubernetesRawResource.class);
  private final String account;
  private final String name;
  private final String namespace;
  private final String displayName;
  private final KubernetesApiVersion apiVersion;
  private final KubernetesKind kind;
  private final Map<String, String> labels;
  private final Moniker moniker;
  private final Long createdTime;

  private KubernetesRawResource(KubernetesManifest manifest, String key, Moniker moniker) {
    this.account = ((Keys.InfrastructureCacheKey) Keys.parseKey(key).get()).getAccount();
    this.name = manifest.getFullResourceName();
    this.displayName = manifest.getName();
    this.apiVersion = manifest.getApiVersion();
    this.kind = manifest.getKind();
    this.namespace = manifest.getNamespace();
    this.labels = ImmutableMap.copyOf(manifest.getLabels());
    this.moniker = moniker;
    this.createdTime = manifest.getCreationTimestampEpochMillis();
  }

  @Nullable
  @ParametersAreNonnullByDefault
  public static KubernetesRawResource fromCacheData(CacheData cd) {
    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);
    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }
    Moniker moniker = KubernetesCacheDataConverter.getMoniker(cd);
    return new KubernetesRawResource(manifest, cd.getId(), moniker);
  }

  public String getRegion() {
    return namespace;
  }
}
