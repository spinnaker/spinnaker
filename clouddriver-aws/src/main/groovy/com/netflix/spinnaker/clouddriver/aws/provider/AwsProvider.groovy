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
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.agent.HealthProvidingCachingAgent
import java.util.regex.Pattern
import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.*

class AwsProvider extends AgentSchedulerAware implements SearchableProvider {

  public static final String PROVIDER_NAME = AwsProvider.name

  final Set<String> defaultCaches = [
    LOAD_BALANCERS.ns,
    CLUSTERS.ns,
    SERVER_GROUPS.ns,
    INSTANCES.ns
  ].asImmutable()

  final Map<String, String> urlMappingTemplates = [
    (SERVER_GROUPS.ns): '/applications/${application.toLowerCase()}/clusters/$account/$cluster/$provider/serverGroups/$serverGroup?region=$region',
    (LOAD_BALANCERS.ns): '/$provider/loadBalancers/$loadBalancer',
    (CLUSTERS.ns): '/applications/${application.toLowerCase()}/clusters/$account/$cluster'
  ].asImmutable()

  final Map<String, SearchableProvider.SearchResultHydrator> searchResultHydrators = [
    (INSTANCES.ns): new InstanceSearchResultHydrator()
  ]

  final Map<String, SearchableProvider.IdentifierExtractor> identifierExtractors

  final Collection<Agent> agents
  private Collection<HealthProvidingCachingAgent> healthAgents

  @Autowired(required = false)
  List<HealthProvidingCachingAgent> externalHealthProvidingCachingAgents = []

  AwsProvider(AccountCredentialsRepository accountCredentialsRepository, Collection<Agent> agents) {
    this.agents = agents
    synchronizeHealthAgents()
    identifierExtractors = [
      (INSTANCES.ns): new InstanceIdentifierExtractor(accountCredentialsRepository)
    ].asImmutable()
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
    allHealthAgents.addAll(this.externalHealthProvidingCachingAgents)
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
      if (!item?.relationships["serverGroups"]) {
        return result
      }

      def serverGroup = Keys.parse(item.relationships["serverGroups"][0])
      return result + [
        application: serverGroup.application as String,
        cluster: serverGroup.cluster as String,
        serverGroup: serverGroup.serverGroup as String
      ]
    }
  }

  static class InstanceIdentifierExtractor implements SearchableProvider.IdentifierExtractor {
    static Pattern INSTANCE_ID_PATTERN = Pattern.compile('(i-)?[0-9a-f]{8}([0-9a-f]{9})?')

    final AccountCredentialsRepository accountCredentialsRepository

    InstanceIdentifierExtractor(AccountCredentialsRepository accountCredentialsRepository) {
      this.accountCredentialsRepository = accountCredentialsRepository
    }

    @Override
    List<String> getIdentifiers(Cache cacheView, String type, String query) {
      if (!query.matches(INSTANCE_ID_PATTERN)) {
        return []
      }
      def normalizedQuery = query.startsWith('i-') ? query : 'i-' + query
      Set<NetflixAmazonCredentials> amazonCredentials = accountCredentialsRepository.all.findAll {
        it instanceof NetflixAmazonCredentials
      } as Set<NetflixAmazonCredentials>

      def possibleInstanceIdentifiers = []
      amazonCredentials.each { NetflixAmazonCredentials credentials ->
        credentials.regions.each { AmazonCredentials.AWSRegion region ->
          possibleInstanceIdentifiers << Keys.getInstanceKey(normalizedQuery, credentials.name, region.name)
        }
      }
      return cacheView.getAll(INSTANCES.ns, possibleInstanceIdentifiers)*.id
    }
  }
}
