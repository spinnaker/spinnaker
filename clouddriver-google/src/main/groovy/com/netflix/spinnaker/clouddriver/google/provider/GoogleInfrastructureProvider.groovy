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
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import groovy.json.JsonOutput

import static com.netflix.spinnaker.clouddriver.cache.SearchableProvider.SearchableResource
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

class GoogleInfrastructureProvider extends AgentSchedulerAware implements SearchableProvider {

  final Collection<Agent> agents
  final String providerName = GoogleInfrastructureProvider.name

  final Set<String> defaultCaches = [
      ADDRESSES.ns,
      APPLICATIONS.ns,
      BACKEND_SERVICES.ns,
      CLUSTERS.ns,
      HEALTH_CHECKS.ns,
      HTTP_HEALTH_CHECKS.ns,
      INSTANCES.ns,
      LOAD_BALANCERS.ns,
      SECURITY_GROUPS.ns,
      SERVER_GROUPS.ns,
      SSL_CERTIFICATES.ns,
  ].asImmutable()

  GoogleInfrastructureProvider(Collection<Agent> agents) {
    this.agents = agents
  }

  final Map<String, String> urlMappingTemplates = [
      (SECURITY_GROUPS.ns): '/securityGroups/$account/$provider/$name?region=$region'
  ]

  final Map<SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators = [
    (new GoogleSearchableResource(ADDRESSES.ns)): new AddressResultHydrator(),
    (new GoogleSearchableResource(BACKEND_SERVICES.ns)): new BackendServiceResultHydrator(),
    (new GoogleSearchableResource(HEALTH_CHECKS.ns)): new HealthCheckResultHydrator(),
    (new GoogleSearchableResource(HTTP_HEALTH_CHECKS.ns)): new HttpHealthCheckResultHydrator(),
    (new GoogleSearchableResource(INSTANCES.ns)): new InstanceSearchResultHydrator()
  ]

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }

  private static class AddressResultHydrator implements SearchableProvider.SearchResultHydrator {

    @Override
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id) {
      CacheData addressCacheData  = cacheView.get(ADDRESSES.ns, id)
      return result + [
          address: JsonOutput.toJson(addressCacheData.attributes.address)
      ]
    }
  }

  private static class BackendServiceResultHydrator implements SearchableProvider.SearchResultHydrator {

    @Override
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id) {
      CacheData backendService  = cacheView.get(BACKEND_SERVICES.ns, id)
      return result + [
          healthCheckLink: backendService.attributes.healthCheckLink as String,
          sessionAffinity: backendService.attributes.sessionAffinity as String,
          affinityCookieTtlSec: backendService.attributes.affinityCookieTtlSec as String,
          enableCDN: backendService.attributes.enableCDN as String,
          region: GCEUtil.getLocalName(backendService.attributes?.region as String)
      ]
    }
  }

  private static class HealthCheckResultHydrator implements SearchableProvider.SearchResultHydrator {

    @Override
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id) {
      CacheData healthCheckCacheData = cacheView.get(HEALTH_CHECKS.ns, id)
      return result + [
        healthCheck: JsonOutput.toJson(healthCheckCacheData.attributes.healthCheck)
      ]
    }
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

  private static class HttpHealthCheckResultHydrator implements SearchableProvider.SearchResultHydrator {

    @Override
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id) {
      CacheData healthCheckCacheData = cacheView.get(HTTP_HEALTH_CHECKS.ns, id)
      return result + [
        httpHealthCheck: JsonOutput.toJson(healthCheckCacheData.attributes.httpHealthCheck)
      ]
    }
  }

  private static class GoogleSearchableResource extends SearchableResource {
    public GoogleSearchableResource(String resourceType) {
      this.resourceType = resourceType.toLowerCase()
      this.platform = 'gce'
    }
  }
}
