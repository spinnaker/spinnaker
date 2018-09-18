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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import com.netflix.spinnaker.clouddriver.model.Manifest.Warning;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public abstract class KubernetesHandler implements CanDeploy, CanDelete, CanPatch {
  protected final static ObjectMapper objectMapper = new ObjectMapper();

  private final ArtifactReplacer artifactReplacer = new ArtifactReplacer();

  abstract public int deployPriority();
  abstract public KubernetesKind kind();
  abstract public boolean versioned();
  abstract public SpinnakerKind spinnakerKind();
  abstract public Status status(KubernetesManifest manifest);

  public List<Warning> listWarnings(KubernetesManifest manifest) {
    return new ArrayList<>();
  }

  public List<String> sensitiveKeys() {
    return new ArrayList<>();
  }

  protected void registerReplacer(ArtifactReplacer.Replacer replacer) {
    artifactReplacer.addReplacer(replacer);
  }

  public ReplaceResult replaceArtifacts(KubernetesManifest manifest, List<Artifact> artifacts, String account) {
    return artifactReplacer.replaceAll(manifest, artifacts, manifest.getNamespace(), account);
  }

  public ReplaceResult replaceArtifacts(KubernetesManifest manifest, List<Artifact> artifacts, String namespace, String account) {
    return artifactReplacer.replaceAll(manifest, artifacts, namespace, account);
  }

  protected Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return null;
  }

  public Set<Artifact> listArtifacts(KubernetesManifest manifest) {
    return artifactReplacer.findAll(manifest);
  }

  public KubernetesV2CachingAgent buildCachingAgent(
      KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      KubernetesResourcePropertyRegistry propertyRegistry,
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
          KubernetesResourcePropertyRegistry.class,
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
          propertyRegistry,
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

  // used for stripping sensitive values
  public void removeSensitiveKeys(KubernetesManifest manifest) {
    List<String> sensitiveKeys = sensitiveKeys();
    sensitiveKeys.forEach(manifest::remove);
  }

  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = objectMapper.convertValue(key, new TypeReference<Map<String, Object>>() {});
    result.put("region", key.getNamespace());
    result.put("name", KubernetesManifest.getFullResourceName(key.getKubernetesKind(), key.getName()));
    return result;
  }

  public void addRelationships(Map<KubernetesKind, List<KubernetesManifest>> allResources, Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) { }

  // lower "value" is deployed before higher "value"
  public enum DeployPriority {
    LOWEST_PRIORITY(1000),
    WORKLOAD_ATTACHMENT_PRIORITY(110),
    WORKLOAD_CONTROLLER_PRIORITY(100),
    WORKLOAD_PRIORITY(100),
    PDB_PRIORITY(90),
    API_SERVICE_PRIORITY(80),
    NETWORK_RESOURCE_PRIORITY(70),
    MOUNTABLE_DATA_PRIORITY(50),
    MOUNTABLE_DATA_BACKING_RESOURCE_PRIORITY(40),
    SERVICE_ACCOUNT_PRIORITY(40),
    STORAGE_CLASS_PRIORITY(40),
    ADMISSION_PRIORITY(40),
    RESOURCE_DEFINITION_PRIORITY(30),
    ROLE_BINDING_PRIORITY(30),
    ROLE_PRIORITY(20),
    NAMESPACE_PRIORITY(0);

    @Getter
    private final int value;

    DeployPriority(int value) {
      this.value = value;
    }

    public static DeployPriority fromString(String val) {
      if (val == null) {
        return null;
      }

      return Arrays.stream(values())
          .filter(v -> v.toString().equalsIgnoreCase(val))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("No such priority '" + val + "'"));
    }
  }
}
