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

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import groovy.util.logging.Slf4j;
import lombok.Data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Data
class KubernetesV2Provider extends AgentSchedulerAware implements SearchableProvider {
  public static final String PROVIDER_NAME = KubernetesCloudProvider.getID();

  final Map<String, String> urlMappingTemplates = Collections.emptyMap();

  final Collection<Agent> agents;
  final KubernetesCloudProvider cloudProvider;

  KubernetesV2Provider(KubernetesCloudProvider cloudProvider, Collection<Agent> agents) {
    this.cloudProvider = cloudProvider;
    this.agents = agents;
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public Set<String> getDefaultCaches() {
    return new HashSet<>();
  }

  @Override
  public Map<SearchableProvider.SearchableResource, SearchableProvider.SearchResultHydrator> getSearchResultHydrators() {
    return new HashMap<>();
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return new HashMap<>();
  }
}
