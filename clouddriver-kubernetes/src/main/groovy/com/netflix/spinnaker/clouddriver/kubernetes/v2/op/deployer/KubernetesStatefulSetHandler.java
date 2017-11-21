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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactTypes;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesStatefulSetCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KubernetesStatefulSetHandler extends KubernetesHandler implements
    CanResize,
    CanDelete,
    CanScale,
    CanPauseRollout,
    CanResumeRollout,
    CanUndoRollout {

  public KubernetesStatefulSetHandler() {
    registerReplacer(
        ArtifactReplacer.Replacer.builder()
            .replacePath("$.spec.template.spec.containers.[?( @.image == \"{%name%}\" )].image")
            .findPath("$.spec.template.spec.containers.*.image")
            .type(ArtifactTypes.DOCKER_IMAGE)
            .build()
    );
  }

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.STATEFUL_SET;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SERVER_GROUPS;
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesStatefulSetCachingAgent.class;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    // TODO(lwander)
    return new Status();
  }

  public static String serviceName(KubernetesManifest manifest) {
    // TODO(lwander) perhaps switch on API version if this changes
    Map<String, Object> spec = (Map<String, Object>) manifest.get("spec");
    return (String) spec.get("serviceName");
  }

  @Override
  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = super.hydrateSearchResult(key, cacheUtils);
    result.put("serverGroup", result.get("name"));

    return result;
  }
}
