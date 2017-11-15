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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.ApplicationCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.ClusterCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Application;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTERS;

@Component
public class KubernetesV2ApplicationProvider implements ApplicationProvider {
  private final KubernetesCacheUtils cacheUtils;

  @Autowired
  KubernetesV2ApplicationProvider(KubernetesCacheUtils cacheUtils) {
    this.cacheUtils = cacheUtils;
  }

  @Override
  public Set<? extends Application> getApplications(boolean expand) {
    if (expand) {
      String clusterGlobKey = Keys.cluster("*", "*", "*");
      Map<String, Set<ClusterCacheKey>> keysByApplication = cacheUtils.getAllKeysMatchingPattern(CLUSTERS.toString(), clusterGlobKey).stream()
          .map(Keys::parseKey)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .filter(ClusterCacheKey.class::isInstance)
          .map(k -> (ClusterCacheKey) k)
          .collect(Collectors.groupingBy(
              ClusterCacheKey::getApplication, Collectors.toSet())
          );

     return keysByApplication.entrySet()
         .stream()
         .map(e -> KubernetesV2Application.builder()
             .name(e.getKey())
             .clusterNames(groupClustersByAccount(e.getValue())).build())
         .collect(Collectors.toSet());
    } else {
      String appGlobKey = Keys.application("*");

      return cacheUtils.getAllKeysMatchingPattern(APPLICATIONS.toString(), appGlobKey)
          .stream()
          .map(Keys::parseKey)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .filter(ApplicationCacheKey.class::isInstance)
          .map(k -> (ApplicationCacheKey) k)
          .map(k -> KubernetesV2Application.builder().name(k.getName()).build())
          .collect(Collectors.toSet());
    }
  }

  @Override
  public Application getApplication(String name) {
    String clusterGlobKey = Keys.cluster("*", name, "*");
    List<ClusterCacheKey> keys = cacheUtils.getAllKeysMatchingPattern(CLUSTERS.toString(), clusterGlobKey)
        .stream()
        .map(Keys::parseKey)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(ClusterCacheKey.class::isInstance)
        .map(k -> (ClusterCacheKey) k)
        .collect(Collectors.toList());

    return KubernetesV2Application.builder()
        .name(name)
        .clusterNames(groupClustersByAccount(keys))
        .build();
  }

  private Map<String, Set<String>> groupClustersByAccount(Collection<ClusterCacheKey> keys) {
    return keys.stream()
        .collect(Collectors.groupingBy(
            ClusterCacheKey::getAccount, Collectors.mapping(ClusterCacheKey::getName, Collectors.toSet())
        ));
  }
}
