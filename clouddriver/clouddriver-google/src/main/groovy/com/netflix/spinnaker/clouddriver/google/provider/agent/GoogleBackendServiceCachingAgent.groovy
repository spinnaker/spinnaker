/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.BackendServiceList
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.BACKEND_SERVICES

@Slf4j
class GoogleBackendServiceCachingAgent extends AbstractGoogleCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(BACKEND_SERVICES.ns)
  ] as Set

  String agentType = "$accountName/$GoogleBackendServiceCachingAgent.simpleName"

  GoogleBackendServiceCachingAgent(String clouddriverUserAgentApplicationName,
                                   GoogleNamedAccountCredentials credentials,
                                   ObjectMapper objectMapper,
                                   Registry registry) {
    super(clouddriverUserAgentApplicationName,
      credentials,
      objectMapper,
      registry)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleBackendService> backendServiceList = loadBackendServices()
    buildCacheResult(providerCache, backendServiceList)
  }

  List<GoogleBackendService> loadBackendServices() {
    List<GoogleBackendService> ret = []

    GoogleBackendServiceCachingAgent cachingAgent = this
    List<BackendService> globalBackendServices = new PaginatedRequest<BackendServiceList>(cachingAgent) {
      @Override
      protected ComputeRequest<BackendServiceList> request (String pageToken) {
        return compute.backendServices().list(project).setPageToken(pageToken)
      }

      @Override
      String getNextPageToken(BackendServiceList t) {
        return t.getNextPageToken();
      }
    }
    .timeExecute(
      { BackendServiceList list -> list.getItems() },
      "compute.backendServices.list",
      TAG_SCOPE, SCOPE_GLOBAL
    )
    if (globalBackendServices) {
      ret.addAll(globalBackendServices.collect { toGoogleBackendService(it, GoogleBackendService.BackendServiceKind.globalBackendService) })
    }

    credentials.regions.collect { it.name }.each { String region ->
      List<BackendService> regionBackendServices = new PaginatedRequest<BackendServiceList>(cachingAgent) {
        @Override
        protected ComputeRequest<BackendServiceList> request (String pageToken) {
          return compute.regionBackendServices().list(project, region).setPageToken(pageToken)
        }

        @Override
        String getNextPageToken(BackendServiceList t) {
          return t.getNextPageToken();
        }
      }
      .timeExecute(
        { BackendServiceList list -> list.getItems()},
        "compute.regionBackendServices.list",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region
      )
      if (regionBackendServices) {
        ret.addAll(regionBackendServices.collect { toGoogleBackendService(it, GoogleBackendService.BackendServiceKind.regionBackendService) })
      }
    }
    ret
  }

  private CacheResult buildCacheResult(ProviderCache _, List<GoogleBackendService> backendServiceList) {
    log.debug("Describing items in ${agentType}")

    def cacheResultBuilder = new CacheResultBuilder()

    backendServiceList.each { GoogleBackendService backendService ->
      def backendServiceKey = Keys.getBackendServiceKey(accountName, backendService.kind as String, backendService.getName())

      cacheResultBuilder.namespace(BACKEND_SERVICES.ns).keep(backendServiceKey).with {
        attributes.name = backendService.name
        attributes.kind = backendService.kind
        attributes.healthCheckLink = backendService.healthCheckLink
        attributes.sessionAffinity = backendService.sessionAffinity
        attributes.affinityCookieTtlSec = backendService.affinityCookieTtlSec
        attributes.region = backendService.region
        attributes.enableCDN = backendService.enableCDN
        attributes.portName = backendService.portName
        attributes.connectionDrainingTimeoutSec = backendService.connectionDrainingTimeoutSec
      }
    }

    log.debug("Caching ${cacheResultBuilder.namespace(BACKEND_SERVICES.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }

  private static GoogleBackendService toGoogleBackendService(BackendService bs, GoogleBackendService.BackendServiceKind kind) {
    new GoogleBackendService(
      name: bs.name,
      kind: kind,
      healthCheckLink: bs.healthChecks[0],
      sessionAffinity: bs.sessionAffinity,
      affinityCookieTtlSec: bs.affinityCookieTtlSec,
      enableCDN: bs.enableCDN,
      region: bs.region ?: 'global',
      portName: bs.portName ?: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME,
      connectionDrainingTimeoutSec: bs.getConnectionDraining()?.getDrainingTimeoutSec() ?: 0,
    )
  }
}
