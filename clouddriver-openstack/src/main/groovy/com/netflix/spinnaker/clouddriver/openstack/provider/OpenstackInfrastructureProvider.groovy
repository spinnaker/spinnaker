/*
 * Copyright 2016 Target Inc.
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
 */

package com.netflix.spinnaker.clouddriver.openstack.provider

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS

@ConditionalOnProperty('openstack.enabled')
class OpenstackInfrastructureProvider extends AgentSchedulerAware implements SearchableProvider {
  public static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  public static final String PROVIDER_NAME = OpenstackInfrastructureProvider.name

  private final Collection<Agent> agents

  OpenstackInfrastructureProvider(Collection<Agent> agents) {
    this.agents = agents
  }

  final Set<String> defaultCaches = [
    APPLICATIONS.ns,
    CLUSTERS.ns,
    INSTANCES.ns,
    SECURITY_GROUPS.ns,
    SERVER_GROUPS.ns
  ].asImmutable()

  //TODO - Need to define urlMappingTemplates
  final Map<String, String> urlMappingTemplates = Collections.emptyMap()
  //TODO - Need to define (if applicable)
  final Map<String, SearchableProvider.SearchResultHydrator> searchResultHydrators = Collections.emptyMap()

  @Override
  Map<String, String> parseKey(String key) {
    Keys.parse(key)
  }

  @Override
  String getProviderName() {
    PROVIDER_NAME
  }

  @Override
  Collection<Agent> getAgents() {
    agents
  }
}
