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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public abstract class KubernetesHandler implements CanDeploy, CanDelete {
  protected final static ObjectMapper objectMapper = new ObjectMapper();

  private final ArtifactReplacer artifactReplacer = new ArtifactReplacer();

  abstract public KubernetesKind kind();
  abstract public boolean versioned();
  abstract public SpinnakerKind spinnakerKind();
  abstract public Status status(KubernetesManifest manifest);

  protected void registerReplacer(ArtifactReplacer.Replacer replacer) {
    artifactReplacer.addReplacer(replacer);
  }

  public ReplaceResult replaceArtifacts(KubernetesManifest manifest, List<Artifact> artifacts) {
    return artifactReplacer.replaceAll(manifest, artifacts);
  }

  protected Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return null;
  }

  public Set<Artifact> listArtifacts(KubernetesManifest manifest) {
    return artifactReplacer.findAll(manifest);
  }

  public KubernetesV2CachingAgent buildCachingAgent(
      KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount
  ) {
    Constructor constructor;
    Class<? extends KubernetesV2CachingAgent> clazz = cachingAgentClass();

    if (clazz == null) {
      log.error("No caching agent was registered for {} -- no resources will be cached", kind());
    }

    try {
      constructor = clazz.getDeclaredConstructor(
          KubernetesNamedAccountCredentials.class,
          ObjectMapper.class,
          Registry.class,
          int.class,
          int.class
      );
    } catch (NoSuchMethodException e) {
      log.warn("Missing canonical constructor for {} caching agent", kind(), e);
      return null;
    }

    try {
      constructor.setAccessible(true);
      return (KubernetesV2CachingAgent) constructor.newInstance(
          namedAccountCredentials,
          objectMapper,
          registry,
          agentIndex,
          agentCount
      );
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      log.warn("Can't invoke caching agent constructor for {} caching agent", kind(), e);
      return null;
    }
  }


  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = objectMapper.convertValue(key, new TypeReference<Map<String, Object>>() {});
    result.put("region", key.getNamespace());
    result.put("name", KubernetesManifest.getFullResourceName(key.getKubernetesKind(), key.getName()));
    return result;
  }
}
