/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.caching

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.cache.KeyParser
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.eureka.provider.agent.EurekaAwareProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.utils.CachingSchema
import com.netflix.spinnaker.clouddriver.titus.caching.utils.CachingSchemaUtil

import javax.inject.Provider

class TitusCachingProvider implements SearchableProvider, EurekaAwareProvider {

  public static final String PROVIDER_NAME = TitusCachingProvider.simpleName

  private final Collection<CachingAgent> agents
  private final KeyParser keyParser = new Keys()

  private Provider<CachingSchemaUtil> cachingSchemaUtil

  TitusCachingProvider(Collection<CachingAgent> agents, Provider<CachingSchemaUtil> cachingSchemaUtil) {
    this.agents = Collections.unmodifiableCollection(agents)
    this.cachingSchemaUtil = cachingSchemaUtil
  }

  @Override
  String getProviderName() {
    PROVIDER_NAME
  }

  @Override
  Collection<Agent> getAgents() {
    agents
  }

  @Override
  Boolean isProviderForEurekaRecord(Map<String, Object> attributes) {
    attributes.containsKey('titusTaskId') && attributes.get('titusTaskId') != null && attributes.get('instanceId') != null
  }

  @Override
  String getInstanceKey(Map<String, Object> attributes, String region) {
    CachingSchema schema = cachingSchemaUtil.get().getCachingSchemaForAccount(attributes.accountId)
    if (schema == CachingSchema.V2) {
      Keys.getInstanceV2Key(attributes.titusTaskId, attributes.accountId, region)
    } else {
      Keys.getInstanceKey(attributes.titusTaskId, attributes.accountId, attributes.titusStack, region)
    }
  }

  @Override
  String getInstanceHealthKey(Map<String, Object> attributes, String region, String healthId) {
    Keys.getInstanceHealthKey(attributes.instanceId, healthId)
  }

  final Set<String> defaultCaches = [
    Keys.Namespace.CLUSTERS.ns,
    Keys.Namespace.SERVER_GROUPS.ns,
    Keys.Namespace.INSTANCES.ns
  ].asImmutable()

  final Map<String, String> urlMappingTemplates = [
    (Keys.Namespace.SERVER_GROUPS.ns) : '/applications/${application.toLowerCase()}/clusters/$account/$cluster/$provider/serverGroups/$serverGroup?region=$region',
    (Keys.Namespace.CLUSTERS.ns)      : '/applications/${application.toLowerCase()}/clusters/$account/$cluster'
  ].asImmutable()

  final Map<SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators = [
    (new TitusSearchableResource(Keys.Namespace.INSTANCES.ns)): new InstanceSearchResultHydrator(),
  ]

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }

  @Override
  Optional<KeyParser> getKeyParser() {
    return Optional.of(keyParser)
  }

  @Override
  boolean supportsSearch(String type, Map<String, String> filters) {
    //this overrides super because we need to search titus if provider=aws...
    final Set<String> searchableProviders = [AmazonCloudProvider.ID, TitusCloudProvider.ID]
    return (
      filters?.cloudProvider == null || searchableProviders.contains(filters.cloudProvider)
    ) && hasAgentForType(type, getAgents())
  }

  private static class InstanceSearchResultHydrator implements SearchableProvider.SearchResultHydrator {
    @Override
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id) {
      def item = cacheView.get(Keys.Namespace.INSTANCES.ns, id)
      if (!item) {
        return null
      }
      if (!item?.relationships["serverGroups"]) {
        return result
      }

      def serverGroup = Keys.parse(item.relationships["serverGroups"][0])
      return result + [
        application: serverGroup.application as String,
        cluster    : serverGroup.cluster as String,
        serverGroup: serverGroup.serverGroup as String
      ]
    }
  }

  private static class TitusSearchableResource extends SearchableResource {
    TitusSearchableResource (String resourceType) {
      this.resourceType = resourceType.toLowerCase()
      this.platform = 'titus'
    }
  }
}
