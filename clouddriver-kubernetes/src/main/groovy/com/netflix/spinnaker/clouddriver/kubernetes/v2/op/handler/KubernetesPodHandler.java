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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer;
import com.netflix.spinnaker.clouddriver.artifacts.kubernetes.KubernetesArtifactType;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_PRIORITY;

@Component
public class KubernetesPodHandler extends KubernetesHandler {
  public KubernetesPodHandler() {
    registerReplacer(
        ArtifactReplacer.Replacer.builder()
            .replacePath("$.spec.containers.[?( @.image == \"{%name%}\" )].image")
            .findPath("$.spec.containers.*.image")
            .type(KubernetesArtifactType.DockerImage)
            .build()
    );
  }

  @Override
  public int deployPriority() {
    return WORKLOAD_PRIORITY.getValue();
  }

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.POD;
  }

  @Override
  public boolean versioned() {
    return true;
  }

  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.INSTANCES;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    Status result = new Status();
    V1Pod pod = KubernetesCacheDataConverter.getResource(manifest, V1Pod.class);
    V1PodStatus status = pod.getStatus();

    if (status == null) {
      result.unstable("No status reported yet")
          .unavailable("No availability reported");
      return result;
    }

    // https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/
    String phase = status.getPhase();

    if (phase == null ) {
      result.unstable("No phase reported yet")
          .unavailable("No availability reported");
    } else if (phase.equals("pending")) {
      result.unstable("Pod is 'pending'")
          .unavailable("Pod has not been scheduled yet");
    } else if (phase.equals("unknown")) {
      result.unstable("Pod has 'unknown' phase")
          .unavailable("No availability reported");
    } else if (phase.equals("failed")) {
      result.failed("Pod has 'failed'")
          .unavailable("Pod is not running");
    }

    return result;
  }

  @Override
  public Map<String, Object> hydrateSearchResult(Keys.InfrastructureCacheKey key, KubernetesCacheUtils cacheUtils) {
    Map<String, Object> result = super.hydrateSearchResult(key, cacheUtils);
    result.put("instanceId", result.get("name"));

    return result;
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesCoreCachingAgent.class;
  }
}
