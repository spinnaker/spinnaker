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

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.ApplicationCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesRawResource;
import com.netflix.spinnaker.clouddriver.kubernetes.config.RawResourcesEndpointConfig;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesRawResourceProvider {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesAccountResolver accountResolver;

  private static final Logger log = LoggerFactory.getLogger(KubernetesRawResourceProvider.class);

  @Autowired
  KubernetesRawResourceProvider(
      KubernetesCacheUtils cacheUtils, KubernetesAccountResolver accountResolver) {
    this.cacheUtils = cacheUtils;
    this.accountResolver = accountResolver;
  }

  public Set<KubernetesRawResource> getApplicationRawResources(String application) {
    return cacheUtils
        .getSingleEntry(APPLICATIONS.toString(), ApplicationCacheKey.createKey(application))
        .map(
            applicationData ->
                fromRawResourceCacheData(cacheUtils.getAllRelationships(applicationData)))
        .orElseGet(ImmutableSet::of);
  }

  private Set<KubernetesRawResource> fromRawResourceCacheData(
      Collection<CacheData> rawResourceData) {
    return rawResourceData.stream()
        .map(KubernetesRawResource::fromCacheData)
        .filter(Objects::nonNull)
        .filter(resource -> includeInResponse(resource))
        .collect(Collectors.toSet());
  }

  private boolean includeInResponse(KubernetesRawResource resource) {
    Optional<KubernetesCredentials> optionalCredentials =
        this.accountResolver.getCredentials(resource.getAccount());

    if (!optionalCredentials.isPresent()) {
      log.warn("Account {} has no credentials", resource.getAccount());
      return false;
    }

    KubernetesCredentials credentials = optionalCredentials.get();
    ImmutableSet<KubernetesKind> omitKinds = credentials.getOmitKinds();
    ImmutableSet<KubernetesKind> kinds = credentials.getKinds();
    RawResourcesEndpointConfig epConfig = credentials.getRawResourcesEndpointConfig();
    List<Pattern> kindPatterns = epConfig.getKindPatterns();
    List<Pattern> omitKindPatterns = epConfig.getOmitKindPatterns();

    log.debug(
        "Kinds: {} OmitKinds: {} KindPatterns: {} OmitKindPatterns: {}",
        kinds.size(),
        omitKinds.size(),
        kindPatterns.size(),
        omitKindPatterns.size());

    // check account level kinds and omitKinds first
    if (!kinds.isEmpty() && !kinds.contains(resource.getKind())) {
      return false;
    }
    if (omitKinds.contains(resource.getKind())) {
      return false;
    }

    // check kindPatterns
    for (Pattern p : kindPatterns) {
      Matcher m = p.matcher(resource.getKind().toString());
      if (m.matches()) {
        return true;
      }
    }
    // check omitKindPatterns
    for (Pattern p : omitKindPatterns) {
      Matcher m = p.matcher(resource.getKind().toString());
      if (m.matches()) {
        return false;
      }
    }
    // It didn't match any filters, default to include
    return true;
  }
}
