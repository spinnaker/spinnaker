/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.provider
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.cf.cache.Keys

import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.SERVER_GROUPS

class CloudFoundryProvider extends AgentSchedulerAware implements SearchableProvider {

  static final String PROVIDER_NAME = CloudFoundryProvider.name

  private final Collection<Agent> agents

  CloudFoundryProvider(Collection<Agent> agents) {
    this.agents = agents
  }

  @Override
  String getProviderName() {
    return PROVIDER_NAME
  }

  @Override
  Collection<Agent> getAgents() {
    agents
  }

  final Set<String> defaultCaches = [SERVER_GROUPS.ns, CLUSTERS.ns].asImmutable()

  final Map<String, String> urlMappingTemplates = [
      (SERVER_GROUPS.ns): '/applications/${application.toLowerCase()}/clusters/$account/$cluster/$provider/serverGroups/$serverGroup?region=$region',
      (CLUSTERS.ns): '/applications/${application.toLowerCase()}/clusters/$account/$cluster'
  ].asImmutable()

  final Map<String, SearchableProvider.SearchResultHydrator> searchResultHydrators = Collections.emptyMap()

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }

}
