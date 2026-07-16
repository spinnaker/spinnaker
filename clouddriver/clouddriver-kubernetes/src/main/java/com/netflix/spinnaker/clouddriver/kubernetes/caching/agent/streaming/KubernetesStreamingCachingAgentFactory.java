/*
 * Copyright 2025 Wise, PLC.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.Front50ApplicationLoader;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import org.springframework.lang.Nullable;

public interface KubernetesStreamingCachingAgentFactory {
  KubernetesStreamingCachingAgent buildCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      KubernetesConfigurationProperties configurationProperties,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      @Nullable Front50ApplicationLoader front50ApplicationLoader);
}
