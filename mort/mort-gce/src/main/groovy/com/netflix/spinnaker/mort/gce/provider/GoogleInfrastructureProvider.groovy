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

package com.netflix.spinnaker.mort.gce.provider

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.mort.gce.cache.Keys

import static com.netflix.spinnaker.mort.gce.cache.Keys.Namespace.SECURITY_GROUPS

class GoogleInfrastructureProvider implements SearchableProvider {
  public static final String PROVIDER_NAME = GoogleInfrastructureProvider.name

  private final GoogleCloudProvider googleCloudProvider
  private final Collection<Agent> agents

  GoogleInfrastructureProvider(GoogleCloudProvider googleCloudProvider, Collection<Agent> agents) {
    this.googleCloudProvider = googleCloudProvider
    this.agents = Collections.unmodifiableCollection(agents)
  }

  @Override
  String getProviderName() {
    return PROVIDER_NAME
  }

  @Override
  Collection<Agent> getAgents() {
    agents
  }

  final Set<String> defaultCaches = [SECURITY_GROUPS.ns].asImmutable()

  final Map<String, String> urlMappingTemplates = [
    (SECURITY_GROUPS.ns): '/securityGroups/$account/$provider/$name?region=$region'
  ]

  final Map<String, SearchableProvider.SearchResultHydrator> searchResultHydrators = Collections.emptyMap()

  final Map<String, SearchableProvider.IdentifierExtractor> identifierExtractors = Collections.emptyMap()

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(googleCloudProvider, key)
  }
}
