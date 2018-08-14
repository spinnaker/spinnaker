/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Getter
public class CloudFoundryProvider extends AgentSchedulerAware implements SearchableProvider {
  // todo(jkschneider): add default caches
  final Set<String> defaultCaches = Collections.emptySet();
  final Map<String, String> urlMappingTemplates = Collections.emptyMap();
  // todo(jkschneider): add search result hydrator
  final Map<SearchableResource, SearchResultHydrator> searchResultHydrators = Collections.emptyMap();
  private final String id = "cloudfoundry";
  private final String providerName = CloudFoundryProvider.class.getName();
  private final Collection<Agent> agents;

  @Override
  public Map<String, String> parseKey(String key) {
    // todo(jkschneider): parse keys
    return Collections.emptyMap();
  }
}
