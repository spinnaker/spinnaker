/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SECURITY_GROUPS

class AwsInfrastructureProvider extends AgentSchedulerAware implements SearchableProvider {
  public static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  public static final String PROVIDER_NAME = AwsInfrastructureProvider.name

  private final AmazonCloudProvider amazonCloudProvider
  private final Collection<Agent> agents

  AwsInfrastructureProvider(AmazonCloudProvider amazonCloudProvider, Collection<Agent> agents) {
    this.amazonCloudProvider = amazonCloudProvider
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

  final Set<String> defaultCaches = [SECURITY_GROUPS.ns].asImmutable()

  final Map<String, String> urlMappingTemplates = [
    (SECURITY_GROUPS.ns): '/securityGroups/$account/$provider/$name?region=$region'
  ]

  final Map<String, SearchableProvider.SearchResultHydrator> searchResultHydrators = Collections.emptyMap()

  final Map<String, SearchableProvider.IdentifierExtractor> identifierExtractors = Collections.emptyMap()

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(amazonCloudProvider, key)
  }
}
