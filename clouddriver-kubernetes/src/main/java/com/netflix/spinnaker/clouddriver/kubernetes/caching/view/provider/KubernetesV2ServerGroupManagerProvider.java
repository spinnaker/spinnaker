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

import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUPS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUP_MANAGERS;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2ServerGroupManager;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesV2ServerGroupManagerCacheData;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManagerProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesV2ServerGroupManagerProvider
    implements ServerGroupManagerProvider<KubernetesV2ServerGroupManager> {
  private final KubernetesCacheUtils cacheUtils;

  @Autowired
  public KubernetesV2ServerGroupManagerProvider(KubernetesCacheUtils cacheUtils) {
    this.cacheUtils = cacheUtils;
  }

  @Override
  public Set<KubernetesV2ServerGroupManager> getServerGroupManagersByApplication(
      String application) {
    CacheData applicationDatum =
        cacheUtils
            .getSingleEntry(
                APPLICATIONS.toString(), Keys.ApplicationCacheKey.createKey(application))
            .orElse(null);
    if (applicationDatum == null) {
      return null;
    }

    Collection<CacheData> serverGroupManagerData =
        cacheUtils.getAllRelationshipsOfSpinnakerKind(
            ImmutableList.of(applicationDatum), SERVER_GROUP_MANAGERS);
    Collection<CacheData> serverGroupData =
        cacheUtils.getAllRelationshipsOfSpinnakerKind(serverGroupManagerData, SERVER_GROUPS);

    Map<String, List<CacheData>> managerToServerGroupMap =
        cacheUtils.mapByRelationship(serverGroupData, SERVER_GROUP_MANAGERS);

    return serverGroupManagerData.stream()
        .map(
            cd ->
                cacheUtils.<KubernetesV2ServerGroupManager>resourceModelFromCacheData(
                    KubernetesV2ServerGroupManagerCacheData.builder()
                        .serverGroupManagerData(cd)
                        .serverGroupData(
                            managerToServerGroupMap.getOrDefault(cd.getId(), new ArrayList<>()))
                        .build()))
        .collect(Collectors.toSet());
  }
}
