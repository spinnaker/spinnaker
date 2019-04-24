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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.CustomKubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2ServerGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2ServerGroupManager;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.ManifestBasedModel;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.data.KubernetesV2CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.data.KubernetesV2ServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.data.KubernetesV2ServerGroupManagerCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.Manifest;
import lombok.extern.slf4j.Slf4j;

public class CustomKubernetesHandlerFactory {
  public static KubernetesHandler create(KubernetesKind kubernetesKind, SpinnakerKind spinnakerKind, boolean versioned, int deployPriority) {
    return new Handler(kubernetesKind, spinnakerKind, versioned, deployPriority);
  }

  @Slf4j
  private static class Handler extends KubernetesHandler implements ModelHandler {
    private final KubernetesKind kubernetesKind;
    private final SpinnakerKind spinnakerKind;
    private final boolean versioned;
    private final int deployPriority;

    Handler(KubernetesKind kubernetesKind, SpinnakerKind spinnakerKind, boolean versioned, int deployPriority) {
      this.kubernetesKind = kubernetesKind;
      this.spinnakerKind = spinnakerKind;
      this.versioned = versioned;
      this.deployPriority = deployPriority;
    }

    @Override
    public int deployPriority() {
      return deployPriority;
    }

    @Override
    public KubernetesKind kind() {
      return kubernetesKind;
    }

    @Override
    public boolean versioned() {
      return versioned;
    }

    @Override
    public SpinnakerKind spinnakerKind() {
      return spinnakerKind;
    }

    @Override
    public Manifest.Status status(KubernetesManifest manifest) {
      return new Manifest.Status();
    }

    @Override
    protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
      return this::buildCustomCachingAgent;
    }

    private KubernetesV2CachingAgent buildCustomCachingAgent(
        KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
        KubernetesResourcePropertyRegistry propertyRegistry,
        ObjectMapper objectMapper,
        Registry registry,
        int agentIndex,
        int agentCount,
        Long agentInterval
    ) {
      return CustomKubernetesCachingAgentFactory.create(
          kubernetesKind,
          namedAccountCredentials,
          propertyRegistry,
          objectMapper,
          registry,
          agentIndex,
          agentCount,
          agentInterval
      );
    }

    @Override
    public ManifestBasedModel fromCacheData(KubernetesV2CacheData cacheData) {
      switch (spinnakerKind()) {
        case SERVER_GROUPS:
          return KubernetesV2ServerGroup.fromCacheData((KubernetesV2ServerGroupCacheData) cacheData);
        case SERVER_GROUP_MANAGERS:
          return KubernetesV2ServerGroupManager.fromCacheData((KubernetesV2ServerGroupManagerCacheData) cacheData);
        default:
          // TODO(dpeach): finish implementing for other SpinnakerKinds.
          log.warn("No default cache data model mapping for Spinnaker kind " + spinnakerKind());
          return null;
      }
    }
  }
}
