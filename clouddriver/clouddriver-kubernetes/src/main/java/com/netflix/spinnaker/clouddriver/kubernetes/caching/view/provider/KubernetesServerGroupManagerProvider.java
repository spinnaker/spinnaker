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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesServerGroupManager;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesServerGroupManagerCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.ServerGroupManagerHandler;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManagerProvider;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesServerGroupManagerProvider
    implements ServerGroupManagerProvider<KubernetesServerGroupManager> {
  private final KubernetesCacheUtils cacheUtils;

  @Autowired
  public KubernetesServerGroupManagerProvider(KubernetesCacheUtils cacheUtils) {
    this.cacheUtils = cacheUtils;
  }

  @Override
  public Set<KubernetesServerGroupManager> getServerGroupManagersByApplication(String application) {
    CacheData applicationDatum =
        cacheUtils
            .getSingleEntry(
                APPLICATIONS.toString(), Keys.ApplicationCacheKey.createKey(application))
            .orElse(null);
    if (applicationDatum == null) {
      return null;
    }

    ImmutableCollection<CacheData> serverGroupManagerData =
        cacheUtils.getRelationships(applicationDatum, SERVER_GROUP_MANAGERS);

    ImmutableMultimap<String, CacheData> managerToServerGroupMap =
        cacheUtils.getRelationships(serverGroupManagerData, SERVER_GROUPS);

    return serverGroupManagerData.stream()
        .map(
            cd ->
                serverGroupManagerFromCacheData(
                    KubernetesServerGroupManagerCacheData.builder()
                        .serverGroupManagerData(cd)
                        .serverGroupData(managerToServerGroupMap.get(cd.getId()))
                        .build()))
        .collect(Collectors.toSet());
  }

  private final ServerGroupManagerHandler DEFAULT_SERVER_GROUP_MANAGER_HANDLER =
      new ServerGroupManagerHandler() {};

  @Nonnull
  private KubernetesServerGroupManager serverGroupManagerFromCacheData(
      @Nonnull KubernetesServerGroupManagerCacheData cacheData) {
    KubernetesHandler handler = cacheUtils.getHandler(cacheData);
    ServerGroupManagerHandler serverGroupManagerHandler =
        handler instanceof ServerGroupManagerHandler
            ? (ServerGroupManagerHandler) handler
            : DEFAULT_SERVER_GROUP_MANAGER_HANDLER;
    return serverGroupManagerHandler.fromCacheData(cacheData);
  }
}
