/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2SecurityGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2SecurityGroupProvider
    implements SecurityGroupProvider<KubernetesV2SecurityGroup> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesSpinnakerKindMap kindMap;

  @Autowired
  KubernetesV2SecurityGroupProvider(
      KubernetesCacheUtils cacheUtils, KubernetesSpinnakerKindMap kindMap) {
    this.cacheUtils = cacheUtils;
    this.kindMap = kindMap;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public Set<KubernetesV2SecurityGroup> getAll(boolean includeRules) {
    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(KubernetesKind::toString)
        .map(cacheUtils::getAllKeys)
        .flatMap(Collection::stream)
        .map(KubernetesV2SecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<KubernetesV2SecurityGroup> getAllByRegion(boolean includeRules, String namespace) {
    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, "*", namespace, "*");
              return cacheUtils.getAllDataMatchingPattern(k.toString(), key);
            })
        .flatMap(Collection::stream)
        .map(KubernetesV2SecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<KubernetesV2SecurityGroup> getAllByAccount(boolean includeRules, String account) {
    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, account, "*", "*");
              return cacheUtils.getAllDataMatchingPattern(k.toString(), key);
            })
        .flatMap(Collection::stream)
        .map(KubernetesV2SecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<KubernetesV2SecurityGroup> getAllByAccountAndName(
      boolean includeRules, String account, String fullName) {
    String name;
    try {
      name = KubernetesManifest.fromFullResourceName(fullName).getRight();
    } catch (Exception e) {
      return null;
    }

    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, account, "*", name);
              return cacheUtils.getAllDataMatchingPattern(k.toString(), key);
            })
        .flatMap(Collection::stream)
        .map(KubernetesV2SecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<KubernetesV2SecurityGroup> getAllByAccountAndRegion(
      boolean includeRule, String account, String namespace) {
    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, account, namespace, "*");
              return cacheUtils.getAllDataMatchingPattern(k.toString(), key);
            })
        .flatMap(Collection::stream)
        .map(KubernetesV2SecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public KubernetesV2SecurityGroup get(
      String account, String namespace, String fullName, String _unused) {
    String name;
    try {
      name = KubernetesManifest.fromFullResourceName(fullName).getRight();
    } catch (Exception e) {
      return null;
    }

    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, account, namespace, name);
              return cacheUtils.getSingleEntry(k.toString(), key).orElse(null);
            })
        .filter(Objects::nonNull)
        .map(KubernetesV2SecurityGroup::fromCacheData)
        .findFirst()
        .orElse(null);
  }

  @Override
  public KubernetesV2SecurityGroup getById(String account, String region, String id, String vpcId) {
    throw new UnsupportedOperationException("Not currently implemented.");
  }
}
