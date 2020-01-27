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

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.NETWORK_RESOURCE_PRIORITY;

import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class KubernetesNetworkPolicyHandler extends KubernetesHandler {
  @Override
  public int deployPriority() {
    return NETWORK_RESOURCE_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.NETWORK_POLICY;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SECURITY_GROUPS;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    return new Status();
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  @Override
  public Map<String, Object> hydrateSearchResult(InfrastructureCacheKey key) {
    Map<String, Object> result = super.hydrateSearchResult(key);
    result.put("id", result.get("name"));

    return result;
  }
}
