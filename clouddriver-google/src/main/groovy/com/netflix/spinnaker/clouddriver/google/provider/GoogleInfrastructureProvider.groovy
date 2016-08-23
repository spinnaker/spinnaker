/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

class GoogleInfrastructureProvider extends AgentSchedulerAware implements SearchableProvider {

  final Collection<Agent> agents
  final String providerName = GoogleInfrastructureProvider.name

  final Set<String> defaultCaches = [
      APPLICATIONS.ns,
      CLUSTERS.ns,
      HTTP_HEALTH_CHECKS.ns,
      INSTANCES.ns,
      LOAD_BALANCERS.ns,
      SECURITY_GROUPS.ns,
      SERVER_GROUPS.ns,
  ].asImmutable()

  GoogleInfrastructureProvider(Collection<Agent> agents) {
    this.agents = agents
  }

  final Map<String, String> urlMappingTemplates = [
      (SECURITY_GROUPS.ns): '/securityGroups/$account/$provider/$name?region=$region'
  ]

  final Map<String, SearchableProvider.SearchResultHydrator> searchResultHydrators = [
      (INSTANCES.ns): new InstanceSearchResultHydrator()
  ]

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }

  private static class InstanceSearchResultHydrator implements SearchableProvider.SearchResultHydrator {

    @Override
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id) {
      def item = cacheView.get(INSTANCES.ns, id)
      if (!item?.relationships["serverGroups"]) {
        return result
      }

      def serverGroup = Keys.parse(item.relationships[SERVER_GROUPS.ns].first())
      return result + [
          application: serverGroup.application as String,
          cluster: serverGroup.cluster as String,
          serverGroup: serverGroup.serverGroup as String
      ]
    }
  }
}
