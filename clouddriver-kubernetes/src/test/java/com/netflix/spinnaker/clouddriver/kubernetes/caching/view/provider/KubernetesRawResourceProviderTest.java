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
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesRawResource;
import com.netflix.spinnaker.clouddriver.kubernetes.config.RawResourcesEndpointConfig;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesRawResourceProviderTest {

  private KubernetesRawResourceProvider provider;
  private KubernetesCredentials credentials;
  private KubernetesAccountResolver accountResolver;

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
    accountResolver = mock(KubernetesAccountResolver.class);
    credentials = mock(KubernetesCredentials.class);
    provider = new KubernetesRawResourceProvider(cacheUtils, accountResolver);

    ImmutableList.Builder<CacheData> cacheDataBuilder = ImmutableList.builder();
    cacheDataBuilder.add(getResourceCacheData(POD_NAME, POD_KIND));
    cacheDataBuilder.add(getResourceCacheData(SECRET_NAME, SECRET_KIND));

    CacheData cacheData = mock(CacheData.class);
    when(cacheUtils.getSingleEntry(
            APPLICATIONS.toString(), Keys.ApplicationCacheKey.createKey(APPLICATION)))
        .thenReturn(Optional.of(cacheData));
    when(cacheUtils.getAllRelationships(cacheData)).thenReturn(cacheDataBuilder.build());
    when(accountResolver.getCredentials(ACCOUNT)).thenReturn(Optional.of(credentials));
  }

  @Test
  void getKubernetesResources() {
    when(credentials.getRawResourcesEndpointConfig()).thenReturn(new RawResourcesEndpointConfig());
    when(credentials.getKinds()).thenReturn(ImmutableSet.of());
    when(credentials.getOmitKinds()).thenReturn(ImmutableSet.of());

    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(2);
    assertThat(rawResources).anyMatch(item -> item.getName().equals(SECRET_FULL_NAME));
    assertThat(rawResources).anyMatch(item -> item.getName().equals(POD_FULL_NAME));
  }

  @Test
  void getKubernetesResourcesWithSpecificKind() {
    when(credentials.getRawResourcesEndpointConfig()).thenReturn(new RawResourcesEndpointConfig());
    when(credentials.getKinds()).thenReturn(ImmutableSet.of(POD_KIND));
    when(credentials.getOmitKinds()).thenReturn(ImmutableSet.of());

    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(1);
    assertThat(rawResources.iterator().next().getName()).isEqualTo(POD_FULL_NAME);
  }

  @Test
  void getKubernetesResourcesOmitingAKind() {
    when(credentials.getRawResourcesEndpointConfig()).thenReturn(new RawResourcesEndpointConfig());
    when(credentials.getKinds()).thenReturn(ImmutableSet.of());
    when(credentials.getOmitKinds()).thenReturn(ImmutableSet.of(POD_KIND));
    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(1);
    assertThat(rawResources.iterator().next().getName()).isEqualTo(SECRET_FULL_NAME);
  }

  @Test
  void getKubernetesResourcesFilteringARawResource() {
    RawResourcesEndpointConfig epConfig = mock(RawResourcesEndpointConfig.class);
    List<String> omitKindExpressions = new ArrayList<>();
    omitKindExpressions.add("^" + POD_KIND.toString() + "$");
    List<Pattern> omitKindPatterns = new ArrayList<>();
    for (String exp : omitKindExpressions) {
      omitKindPatterns.add(Pattern.compile(exp));
    }
    when(epConfig.getOmitKindPatterns()).thenReturn(omitKindPatterns);
    when(credentials.getRawResourcesEndpointConfig()).thenReturn(epConfig);
    when(credentials.getKinds()).thenReturn(ImmutableSet.of());
    when(credentials.getOmitKinds()).thenReturn(ImmutableSet.of());

    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(1);
    assertThat(rawResources.iterator().next().getName()).isEqualTo(SECRET_FULL_NAME);
  }

  @Test
  void getKubernetesResourcesIncludingThenFilteringAKind() {
    RawResourcesEndpointConfig epConfig = mock(RawResourcesEndpointConfig.class);
    List<String> kindExpressions = new ArrayList<>();
    kindExpressions.add("^" + POD_KIND.toString() + "$");
    List<Pattern> kindPatterns = new ArrayList<>();
    for (String exp : kindExpressions) {
      kindPatterns.add(Pattern.compile(exp));
    }
    when(epConfig.getOmitKindPatterns()).thenReturn(kindPatterns);
    when(credentials.getRawResourcesEndpointConfig()).thenReturn(epConfig);
    when(credentials.getKinds()).thenReturn(ImmutableSet.of(POD_KIND, SECRET_KIND));
    when(credentials.getOmitKinds()).thenReturn(ImmutableSet.of());

    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(1);
  }

  @Test
  void getKubernetesResourcesFilteringAllResources() {
    RawResourcesEndpointConfig epConfig = mock(RawResourcesEndpointConfig.class);
    List<String> kindExpressions = new ArrayList<>();
    kindExpressions.add(".*");
    List<Pattern> kindPatterns = new ArrayList<>();
    for (String exp : kindExpressions) {
      kindPatterns.add(Pattern.compile(exp));
    }
    when(epConfig.getOmitKindPatterns()).thenReturn(kindPatterns);
    when(credentials.getRawResourcesEndpointConfig()).thenReturn(epConfig);
    when(credentials.getKinds()).thenReturn(ImmutableSet.of());
    when(credentials.getOmitKinds()).thenReturn(ImmutableSet.of());

    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(0);
  }

  @Test
  void getKubernetesResourcesFilteringAllExceptOne() {
    RawResourcesEndpointConfig epConfig = mock(RawResourcesEndpointConfig.class);
    List<String> kindExpressions = new ArrayList<>();
    kindExpressions.add("^" + POD_KIND.toString() + "$");
    List<Pattern> kindPatterns = new ArrayList<>();
    for (String exp : kindExpressions) {
      kindPatterns.add(Pattern.compile(exp));
    }
    List<String> omitKindExpressions = new ArrayList<>();
    omitKindExpressions.add(".*");
    List<Pattern> omitKindPatterns = new ArrayList<>();
    for (String exp : omitKindExpressions) {
      omitKindPatterns.add(Pattern.compile(exp));
    }
    when(epConfig.getOmitKindPatterns()).thenReturn(omitKindPatterns);
    when(epConfig.getKindPatterns()).thenReturn(kindPatterns);
    when(credentials.getRawResourcesEndpointConfig()).thenReturn(epConfig);
    when(credentials.getKinds()).thenReturn(ImmutableSet.of());
    when(credentials.getOmitKinds()).thenReturn(ImmutableSet.of());

    Set<KubernetesRawResource> rawResources = provider.getApplicationRawResources(APPLICATION);

    assertThat(rawResources.size()).isEqualTo(1);
    assertThat(rawResources.iterator().next().getName()).isEqualTo(POD_FULL_NAME);
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
