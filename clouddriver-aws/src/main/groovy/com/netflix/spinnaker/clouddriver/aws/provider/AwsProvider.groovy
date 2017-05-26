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

package com.netflix.spinnaker.clouddriver.aws.provider

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.eureka.provider.agent.EurekaAwareProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent
import java.util.regex.Pattern

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*
import static com.netflix.spinnaker.clouddriver.cache.SearchableProvider.SearchableResource

class AwsProvider extends AgentSchedulerAware implements SearchableProvider, EurekaAwareProvider {

  public static final String PROVIDER_NAME = AwsProvider.name

  final AccountCredentialsRepository accountCredentialsRepository

  final Set<String> defaultCaches = [
    LOAD_BALANCERS.ns,
    CLUSTERS.ns,
    SERVER_GROUPS.ns,
    TARGET_GROUPS.ns,
    INSTANCES.ns
  ].asImmutable()

  final Map<String, String> urlMappingTemplates = [
    (SERVER_GROUPS.ns) : '/applications/${application.toLowerCase()}/clusters/$account/$cluster/$provider/serverGroups/$serverGroup?region=$region',
    (LOAD_BALANCERS.ns): '/$provider/loadBalancers/$loadBalancer',
    (CLUSTERS.ns)      : '/applications/${application.toLowerCase()}/clusters/$account/$cluster'
  ].asImmutable()

  final Map<SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators = [
    (new AmazonSearchableResource(INSTANCES.ns)): new InstanceSearchResultHydrator(),
  ]

  final Collection<Agent> agents
  private Collection<HealthProvidingCachingAgent> healthAgents

  AwsProvider(AccountCredentialsRepository accountCredentialsRepository, Collection<Agent> agents) {
    this.agents = agents
    this.accountCredentialsRepository = accountCredentialsRepository
    synchronizeHealthAgents()
  }

  void synchronizeHealthAgents() {
    this.healthAgents = Collections.unmodifiableCollection(agents.findAll {
      it instanceof HealthProvidingCachingAgent
    } as List<HealthProvidingCachingAgent>)
  }

  @Override
  String getProviderName() {
    return PROVIDER_NAME
  }

  Collection<HealthProvidingCachingAgent> getHealthAgents() {
    def allHealthAgents = []
    allHealthAgents.addAll(this.healthAgents)
    Collections.unmodifiableCollection(allHealthAgents)
  }

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }

  private static class InstanceSearchResultHydrator implements SearchableProvider.SearchResultHydrator {
    @Override
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id) {
      def item = cacheView.get(INSTANCES.ns, id)
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

  // Eureka provider support
  @Override
  Boolean isProviderForEurekaRecord(Map<String, Object> attributes) {
    attributes.containsKey('accountId')
  }

  @Override
  String getInstanceKey(Map<String, Object> attributes, String region) {
    def credentialName = getCredentialName(attributes.accountId, attributes.allowMultipleEurekaPerAccount, attributes.eurekaAccountName)
    if (credentialName == null) {
      return null
    }
    Keys.getInstanceKey(attributes.instanceId, credentialName, region)
  }

  @Override
  String getInstanceHealthKey(Map<String, Object> attributes, String region, String healthId) {
    Keys.getInstanceHealthKey(attributes.instanceId, getCredentialName(attributes.accountId, attributes.allowMultipleEurekaPerAccount, attributes.eurekaAccountName), region, healthId)
  }

  private String getCredentialName(String accountId, boolean allowMultipleEurekaPerAccount, String eurekaAccountName) {
    if (allowMultipleEurekaPerAccount) {
      def credentialName = accountCredentialsRepository.all.find {
        it instanceof NetflixAmazonCredentials && it.accountId == accountId && it.name == eurekaAccountName
      }?.name
      if (credentialName) {
        return credentialName
      }
    }
    return accountCredentialsRepository.all.find {
      it instanceof NetflixAmazonCredentials && it.accountId == accountId
    }?.name
  }

  private static class AmazonSearchableResource extends SearchableResource {
    public AmazonSearchableResource(String resourceType) {
      this.resourceType = resourceType.toLowerCase()
      this.platform = 'aws'
    }
  }
}
