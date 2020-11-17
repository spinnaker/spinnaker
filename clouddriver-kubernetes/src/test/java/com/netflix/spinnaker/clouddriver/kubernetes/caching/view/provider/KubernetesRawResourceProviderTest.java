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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.APPLICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesRawResource;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.moniker.Moniker;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesRawResourceProviderTest {

  private KubernetesRawResourceProvider provider;
  private KubernetesConfigurationProperties config;

  private static final String APPLICATION = "application";
  private static final String ACCOUNT = "account";
  private static final String NAMESPACE = "namespace";

  private static final String SECRET_NAME = "mysecret";
  private static final KubernetesKind SECRET_KIND = KubernetesKind.SECRET;
  private static final String SECRET_FULL_NAME = SECRET_KIND + " " + SECRET_NAME;

  private static final String POD_NAME = "mypod";
  private static final KubernetesKind POD_KIND = KubernetesKind.POD;
  private static final String POD_FULL_NAME = POD_KIND + " " + POD_NAME;

  @BeforeEach
  public void setup() {
    KubernetesCacheUtils cacheUtils = mock(KubernetesCacheUtils.class);
    config = new KubernetesConfigurationProperties();
    provider = new KubernetesRawResourceProvider(cacheUtils, config);

    ImmutableList.Builder<CacheData> cacheDataBuilder = ImmutableList.builder();
    cacheDataBuilder.add(getResourceCacheData(POD_NAME, POD_KIND));
    cacheDataBuilder.add(getResourceCacheData(SECRET_NAME, SECRET_KIND));

    CacheData cacheData = mock(CacheData.class);
    when(cacheUtils.getSingleEntry(
            APPLICATIONS.toString(), Keys.ApplicationCacheKey.createKey(APPLICATION)))
        .thenReturn(Optional.of(cacheData));
    when(cacheUtils.getAllRelationships(cacheData)).thenReturn(cacheDataBuilder.build());
  }

  @Test
  void getKubernetesResources() {
    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(2);
    assertThat(rawResources).anyMatch(item -> item.getName().equals(SECRET_FULL_NAME));
    assertThat(rawResources).anyMatch(item -> item.getName().equals(POD_FULL_NAME));
  }

  @Test
  void getKubernetesResourcesWithSpecificKind() {
    Set<String> kindsSet = new HashSet<>();
    kindsSet.add(POD_KIND.toString());
    config.getRawResourcesEndpointConfig().setKinds(kindsSet);

    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(1);
    assertThat(rawResources.iterator().next().getName()).isEqualTo(POD_FULL_NAME);
  }

  @Test
  void getKubernetesResourcesOmitingAKind() {
    Set<String> omitKindsSet = new HashSet<>();
    omitKindsSet.add(POD_KIND.toString());
    config.getRawResourcesEndpointConfig().setOmitKinds(omitKindsSet);

    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(1);
    assertThat(rawResources.iterator().next().getName()).isEqualTo(SECRET_FULL_NAME);
  }

  private CacheData getResourceCacheData(String name, KubernetesKind kind) {
    String cacheKey = Keys.InfrastructureCacheKey.createKey(kind, ACCOUNT, NAMESPACE, name);
    CacheData cacheData = mock(CacheData.class);
    when(cacheData.getId()).thenReturn(cacheKey);

    Map<String, Object> attributes = new HashMap<>();
    KubernetesManifest manifest = new KubernetesManifest();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("creationTimestamp", Instant.now().toString());
    manifest.put("metadata", metadata);
    manifest.setApiVersion(KubernetesApiVersion.V1);
    manifest.setKind(kind);
    manifest.setName(name);
    manifest.setNamespace(NAMESPACE);
    attributes.put("manifest", manifest);

    attributes.put("moniker", Moniker.builder().app(APPLICATION).cluster(ACCOUNT).build());
    when(cacheData.getAttributes()).thenReturn(attributes);
    return cacheData;
  }
}
