/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.common

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.Keys

import static com.netflix.spinnaker.clouddriver.azure.common.Keys.Namespace.SECURITY_GROUPS

class AzureInfrastructureProvider implements SearchableProvider {
  public static final String PROVIDER_NAME = AzureInfrastructureProvider.name

  private final AzureCloudProvider azureCloudProvider
  private final Collection<Agent> agents

  AzureInfrastructureProvider(AzureCloudProvider azureCloudProvider, Collection<Agent> agents) {
    this.azureCloudProvider = azureCloudProvider
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
    return Keys.parse(azureCloudProvider, key)
  }
}
