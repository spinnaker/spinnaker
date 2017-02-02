/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.provider

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider

class AppengineProvider extends AgentSchedulerAware implements SearchableProvider {
  public static final String PROVIDER_NAME = AppengineProvider.name

  final Map<String, String> urlMappingTemplates = Collections.emptyMap()
  final Map<SearchableProvider.SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators = Collections.emptyMap()
  final Collection<Agent> agents
  final AppengineCloudProvider cloudProvider
  final Set<String> defaultCaches = [
    Keys.Namespace.APPLICATIONS.ns,
    Keys.Namespace.CLUSTERS.ns,
    Keys.Namespace.SERVER_GROUPS.ns,
    Keys.Namespace.INSTANCES.ns,
    Keys.Namespace.LOAD_BALANCERS.ns,
  ].asImmutable()

  AppengineProvider(AppengineCloudProvider cloudProvider, Collection<Agent> agents) {
    this.cloudProvider = cloudProvider
    this.agents = agents
  }

  @Override
  String getProviderName() {
    return PROVIDER_NAME
  }

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }
}
