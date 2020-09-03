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
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesSecurityGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesSecurityGroupProvider
    implements SecurityGroupProvider<KubernetesSecurityGroup> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesSpinnakerKindMap kindMap;

  @Autowired
  KubernetesSecurityGroupProvider(
      KubernetesCacheUtils cacheUtils, KubernetesSpinnakerKindMap kindMap) {
    this.cacheUtils = cacheUtils;
    this.kindMap = kindMap;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public Set<KubernetesSecurityGroup> getAll(boolean includeRules) {
    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(KubernetesKind::toString)
        .map(cacheUtils::getAllKeys)
        .flatMap(Collection::stream)
        .map(KubernetesSecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<KubernetesSecurityGroup> getAllByRegion(boolean includeRules, String namespace) {
    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, "*", namespace, "*");
              return cacheUtils.getAllDataMatchingPattern(k.toString(), key);
            })
        .flatMap(Collection::stream)
        .map(KubernetesSecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<KubernetesSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, account, "*", "*");
              return cacheUtils.getAllDataMatchingPattern(k.toString(), key);
            })
        .flatMap(Collection::stream)
        .map(KubernetesSecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<KubernetesSecurityGroup> getAllByAccountAndName(
      boolean includeRules, String account, String fullName) {
    String name;
    try {
      name = KubernetesCoordinates.builder().fullResourceName(fullName).build().getName();
    } catch (IllegalArgumentException e) {
      return null;
    }

    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, account, "*", name);
              return cacheUtils.getAllDataMatchingPattern(k.toString(), key);
            })
        .flatMap(Collection::stream)
        .map(KubernetesSecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<KubernetesSecurityGroup> getAllByAccountAndRegion(
      boolean includeRule, String account, String namespace) {
    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, account, namespace, "*");
              return cacheUtils.getAllDataMatchingPattern(k.toString(), key);
            })
        .flatMap(Collection::stream)
        .map(KubernetesSecurityGroup::fromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public KubernetesSecurityGroup get(
      String account, String namespace, String fullName, String _unused) {
    String name;
    try {
      name = KubernetesCoordinates.builder().fullResourceName(fullName).build().getName();
    } catch (IllegalArgumentException e) {
      return null;
    }

    return kindMap.translateSpinnakerKind(SpinnakerKind.SECURITY_GROUPS).stream()
        .map(
            k -> {
              String key = Keys.InfrastructureCacheKey.createKey(k, account, namespace, name);
              return cacheUtils.getSingleEntry(k.toString(), key).orElse(null);
            })
        .filter(Objects::nonNull)
        .map(KubernetesSecurityGroup::fromCacheData)
        .findFirst()
        .orElse(null);
  }

  @Override
  public KubernetesSecurityGroup getById(String account, String region, String id, String vpcId) {
    throw new UnsupportedOperationException("Not currently implemented.");
  }
}
