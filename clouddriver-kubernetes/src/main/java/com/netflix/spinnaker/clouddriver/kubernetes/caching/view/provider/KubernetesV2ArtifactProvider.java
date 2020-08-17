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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Comparator;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesV2ArtifactProvider implements ArtifactProvider {
  private final KubernetesCacheUtils cacheUtils;
  private final ObjectMapper objectMapper;

  @Autowired
  KubernetesV2ArtifactProvider(KubernetesCacheUtils cacheUtils, ObjectMapper objectMapper) {
    this.cacheUtils = cacheUtils;
    this.objectMapper = objectMapper;
  }

  @Override
  public ImmutableList<Artifact> getArtifacts(
      String type, String name, String location, @Nonnull String account) {
    String key = Keys.ArtifactCacheKey.createKey(type, name, location, "*");
    return cacheUtils.getAllDataMatchingPattern(Keys.Kind.ARTIFACT.toString(), key).stream()
        .sorted(
            Comparator.comparing(
                cd -> (String) cd.getAttributes().getOrDefault("creationTimestamp", "")))
        .map(this::cacheDataToArtifact)
        .filter(a -> account.equals(a.getMetadata("account")))
        .collect(toImmutableList());
  }

  private Artifact cacheDataToArtifact(CacheData cacheData) {
    return objectMapper.convertValue(cacheData.getAttributes().get("artifact"), Artifact.class);
  }
}
