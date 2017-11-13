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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import groovy.util.logging.Slf4j;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
class KubernetesV2Provider extends AgentSchedulerAware implements SearchableProvider {
  public static final String PROVIDER_NAME = KubernetesCloudProvider.getID();

  private final static ObjectMapper mapper = new ObjectMapper();
  private final Map<String, String> urlMappingTemplates = Collections.emptyMap();
  private final Collection<Agent> agents = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final KubernetesCloudProvider cloudProvider;
  private final KubernetesSpinnakerKindMap kindMap;

  KubernetesV2Provider(KubernetesCloudProvider cloudProvider, KubernetesSpinnakerKindMap kindMap) {
    this.cloudProvider = cloudProvider;
    this.kindMap = kindMap;
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public Set<String> getDefaultCaches() {
    return kindMap.allKubernetesKinds()
        .stream()
        .map(KubernetesKind::toString)
        .collect(Collectors.toSet());
  }

  @Override
  public Map<SearchableProvider.SearchableResource, SearchableProvider.SearchResultHydrator> getSearchResultHydrators() {
    return new HashMap<>();
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return (Map<String, String>) Keys.parseKey(key)
        .map(k -> {
          String group = k.getGroup();
          try {
            KubernetesKind kind = KubernetesKind.fromString(group);
            k.setType(kindMap.translateKubernetesKind(kind).toString());
          } catch (Exception _ignored) {
            k.setType(group);
          }

          return k;
        })
        .map(k -> mapper.convertValue(k, new TypeReference<Map<String, String>>() {}))
        .orElse(null);
  }
}
