/*
 * Copyright 2018 Google, Inc.
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

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind.EVENT;

import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;

@Component
public class KubernetesEventHandler extends KubernetesHandler {
  @Override
  public int deployPriority() {
    throw new IllegalStateException("Events cannot be deployed.");
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return EVENT;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.UNCLASSIFIED;
  }

  @Override
  public Manifest.Status status(KubernetesManifest manifest) {
    return Status.defaultStatus();
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  @Override
  public void addRelationships(
      Map<KubernetesKind, List<KubernetesManifest>> allResources,
      Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    relationshipMap.putAll(
        allResources.getOrDefault(EVENT, new ArrayList<>()).stream()
            .map(
                m ->
                    ImmutablePair.of(
                        m,
                        involvedManifest(
                            KubernetesCacheDataConverter.getResource(m, V1Event.class))))
            .filter(p -> p.getRight() != null)
            .collect(
                Collectors.toMap(
                    ImmutablePair::getLeft, p -> Collections.singletonList(p.getRight()))));
  }

  private KubernetesManifest involvedManifest(V1Event event) {
    if (event == null) {
      return null;
    }

    V1ObjectReference ref = event.getInvolvedObject();

    if (ref == null
        || StringUtils.isEmpty(ref.getApiVersion())
        || StringUtils.isEmpty(ref.getKind())
        || StringUtils.isEmpty(ref.getName())) {
      return null;
    }

    KubernetesManifest result = new KubernetesManifest();
    result.put("metadata", new HashMap<String, Object>());
    result.setApiVersion(KubernetesApiVersion.fromString(ref.getApiVersion()));
    result.setKind(KubernetesKind.fromString(ref.getKind()));
    result.setNamespace(ref.getNamespace());
    result.setName(ref.getName());
    return result;
  }
}
