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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class KubernetesHandler {
  @Autowired
  protected ObjectMapper objectMapper;

  @Getter
  @Autowired
  protected KubectlJobExecutor jobExecutor;

  private ArtifactReplacer artifactReplacer = new ArtifactReplacer();

  protected void registerReplacer(ArtifactReplacer.Replacer replacer) {
    artifactReplacer.addReplacer(replacer);
  }

  public ReplaceResult replaceArtifacts(KubernetesManifest manifest, List<Artifact> artifacts) {
    return artifactReplacer.replaceAll(manifest, artifacts);
  }

  public Set<Artifact> listArtifacts(KubernetesManifest manifest) {
    return artifactReplacer.findAll(manifest);
  }

  public OperationResult deployAugmentedManifest(KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    deploy(credentials, manifest);
    return new OperationResult().addManifest(manifest);
  }

  abstract public KubernetesKind kind();
  abstract public boolean versioned();
  abstract public SpinnakerKind spinnakerKind();
  abstract public Status status(KubernetesManifest manifest);
  abstract public Class<? extends KubernetesV2CachingAgent> cachingAgentClass();

  protected void deploy(KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    credentials.deploy(manifest);
  }

  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = objectMapper.convertValue(key, new TypeReference<Map<String, Object>>() {});
    result.put("region", key.getNamespace());
    result.put("name", KubernetesManifest.getFullResourceName(key.getKubernetesKind(), key.getName()));
    return result;
  }
}
