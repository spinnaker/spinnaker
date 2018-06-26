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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Component
class GoogleLoadBalancerProvider implements LoadBalancerProvider<GoogleLoadBalancerView> {

  final String cloudProvider = GoogleCloudProvider.ID

  @Autowired
  Cache cacheView
  @Autowired
  ObjectMapper objectMapper
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  Set<GoogleLoadBalancerView> getApplicationLoadBalancers(String application) {
    def pattern = Keys.getLoadBalancerKey("*", "*", "${application}*")
    def identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, pattern)

    def applicationServerGroups = cacheView.getAll(
        SERVER_GROUPS.ns,
        cacheView.filterIdentifiers(SERVER_GROUPS.ns, "${GoogleCloudProvider.ID}:*:${application}-*")
    )
    applicationServerGroups.each { CacheData serverGroup ->
      identifiers.addAll(serverGroup.relationships[LOAD_BALANCERS.ns] ?: [])
    }

    // TODO(duftler): De-frigga this.

    cacheView.getAll(LOAD_BALANCERS.ns,
                     identifiers.unique(),
                     RelationshipCacheFilter.include(SERVER_GROUPS.ns, INSTANCES.ns)).collect { CacheData loadBalancerCacheData ->
      loadBalancersFromCacheData(loadBalancerCacheData, (loadBalancerCacheData?.relationships?.get(INSTANCES.ns) ?: []) as Set)
    } as Set
  }

  GoogleLoadBalancerView loadBalancersFromCacheData(CacheData loadBalancerCacheData, Set<String> allApplicationInstanceKeys) {
    GoogleLoadBalancer loadBalancer = null
    switch (GoogleLoadBalancerType.valueOf(loadBalancerCacheData.attributes?.type as String)) {
      case GoogleLoadBalancerType.INTERNAL:
        loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleInternalLoadBalancer)
        break
      case GoogleLoadBalancerType.HTTP:
        loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleHttpLoadBalancer)
        break
      case GoogleLoadBalancerType.NETWORK:
        loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleNetworkLoadBalancer)
        break
      case GoogleLoadBalancerType.SSL:
        loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleSslLoadBalancer)
        break
      case GoogleLoadBalancerType.TCP:
        loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleTcpLoadBalancer)
        break
      default:
        loadBalancer = null
        break
    }

    GoogleLoadBalancerView loadBalancerView = loadBalancer?.view

    def serverGroupKeys = loadBalancerCacheData?.relationships[SERVER_GROUPS.ns]
    if (!serverGroupKeys) {
      return loadBalancerView
    }
    cacheView.getAll(SERVER_GROUPS.ns, serverGroupKeys)?.each { CacheData serverGroupCacheData ->
      if (!serverGroupCacheData) {
        return
      }

      GoogleServerGroup serverGroup = objectMapper.convertValue(serverGroupCacheData.attributes, GoogleServerGroup)

      // We have to calculate the L7, ILB, or SSL disabled state with respect to this server group since it's not
      // set on the way to the cache.
      Boolean isDisabled = false
      switch (loadBalancer.type) {
        case GoogleLoadBalancerType.HTTP:
          def isDisabledFromHttp = Utils.determineHttpLoadBalancerDisabledState(loadBalancer, serverGroup)
          isDisabled = serverGroup.asg.get(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES) ? // We assume these are L4 load balancers, and the state has been calculated on the way to the cache.
            isDisabledFromHttp && serverGroup.disabled : isDisabledFromHttp
          break
        case GoogleLoadBalancerType.INTERNAL:
          // A server group shouldn't be internally and externally (L4/L7/SSL) load balanced at the same time.
          isDisabled = Utils.determineInternalLoadBalancerDisabledState(loadBalancer, serverGroup)
          break
        case GoogleLoadBalancerType.NETWORK:
          isDisabled = serverGroup.disabled
          break
        case GoogleLoadBalancerType.SSL:
          def isDisabledFromSsl = Utils.determineSslLoadBalancerDisabledState(loadBalancer, serverGroup)
          isDisabled = serverGroup.asg.get(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES) ? // We assume these are L4 load balancers, and the state has been calculated on the way to the cache.
            isDisabledFromSsl && serverGroup.disabled : isDisabledFromSsl
          break
        case GoogleLoadBalancerType.TCP:
          def isDisabledFromTcp = Utils.determineTcpLoadBalancerDisabledState(loadBalancer, serverGroup)
          isDisabled = serverGroup.asg.get(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES) ? // We assume these are L4 load balancers, and the state has been calculated on the way to the cache.
            isDisabledFromTcp && serverGroup.disabled : isDisabledFromTcp
          break
        default:
          throw new IllegalStateException("Illegal type ${loadBalancer.type} for load balancer ${loadBalancer.name}")
          break
      }

      def loadBalancerServerGroup = new LoadBalancerServerGroup(
          name: serverGroup.name,
          region: serverGroup.region,
          isDisabled: isDisabled,
          detachedInstances: [],
          instances: [],
      )

      // TODO(duftler): De-frigga this.
      def serverGroupInstancePattern = Keys.getInstanceKey(loadBalancer.account, serverGroup.region, "$serverGroup.name-.*")
      def instanceKeys = allApplicationInstanceKeys.findAll { it ==~ serverGroupInstancePattern }
      def instanceNames = instanceKeys.collect {
        Keys.parse(it)?.name
      }

      loadBalancer.healths.each { GoogleLoadBalancerHealth googleLoadBalancerHealth ->
        if (!instanceNames.remove(googleLoadBalancerHealth.instanceName)) {
          return
        }

        loadBalancerServerGroup.instances << new LoadBalancerInstance(
            id: googleLoadBalancerHealth.instanceName,
            zone: googleLoadBalancerHealth.instanceZone,
            health: [
                "state"      : googleLoadBalancerHealth.lbHealthSummaries[0].state as String,
                "description": googleLoadBalancerHealth.lbHealthSummaries[0].description
            ]
        )
      }

      loadBalancerServerGroup.detachedInstances = instanceNames // Any remaining instances are considered detached.
      loadBalancerView.serverGroups << loadBalancerServerGroup
    }

    return loadBalancerView
  }

  List<GoogleLoadBalancerAccountRegionSummary> list() {
    def loadBalancerViewsByName = getApplicationLoadBalancers("").groupBy { it.name }

    loadBalancerViewsByName.collect { String name, List<GoogleLoadBalancerView> views ->
      def summary = new GoogleLoadBalancerAccountRegionSummary(name: name)

      views.each { GoogleLoadBalancerView view ->
        def loadBalancerType = view.loadBalancerType
        def backendServices = []
        def urlMapName
        switch (loadBalancerType) {
          case (GoogleLoadBalancerType.HTTP):
            GoogleHttpLoadBalancer.View httpView = view as GoogleHttpLoadBalancer.View
            if (httpView.defaultService) {
              backendServices << httpView?.defaultService.name
            }
            httpView?.hostRules?.each { GoogleHostRule hostRule ->
              backendServices << hostRule?.pathMatcher?.defaultService?.name
              hostRule?.pathMatcher?.pathRules?.each { GooglePathRule pathRule ->
                backendServices << pathRule.backendService.name
              }
            }
            urlMapName = httpView.urlMapName
            break
          case (GoogleLoadBalancerType.INTERNAL):
            GoogleInternalLoadBalancer.View ilbView = view as GoogleInternalLoadBalancer.View
            backendServices << ilbView.backendService.name
            break
          case (GoogleLoadBalancerType.SSL):
            GoogleSslLoadBalancer.View sslView = view as GoogleSslLoadBalancer.View
            backendServices << sslView.backendService.name
            break
          case (GoogleLoadBalancerType.TCP):
            GoogleTcpLoadBalancer.View tcpView = view as GoogleTcpLoadBalancer.View
            backendServices << tcpView.backendService.name
            break
          default:
            // No backend services to add.
            break
        }

        summary.mappedAccounts[view.account].mappedRegions[view.region].loadBalancers << new GoogleLoadBalancerSummary(
            account: view.account,
            region: view.region,
            name: view.name,
            loadBalancerType: loadBalancerType,
            backendServices: backendServices.unique() as List<String> ?: null,
            urlMapName: urlMapName
        )
      }

      summary
    }
  }

  GoogleLoadBalancerAccountRegionSummary get(String name) {
    // TODO(ttomsu): It's inefficient to pull everything back and (possibly) discard most of it.
    // Refactor when addressing https://github.com/spinnaker/spinnaker/issues/807
    list().find { it.name == name }
  }

  List<GoogleLoadBalancerDetails> byAccountAndRegionAndName(String account,
                                                            String region,
                                                            String name) {
    GoogleLoadBalancerView view = getApplicationLoadBalancers(name).find { view ->
      view.account == account && view.region == region
    }

    if (!view) {
      return []
    }

    def backendServiceHealthChecks = [:]
    if (view.loadBalancerType == GoogleLoadBalancerType.HTTP) {
      GoogleHttpLoadBalancer.View httpView = view as GoogleHttpLoadBalancer.View
      List<GoogleBackendService> backendServices = Utils.getBackendServicesFromHttpLoadBalancerView(httpView)
      backendServices?.each { GoogleBackendService backendService ->
        backendServiceHealthChecks[backendService.name] = backendService.healthCheck.view
      }
    }

    String instancePort
    String loadBalancerPort
    switch (view.loadBalancerType) {
      case GoogleLoadBalancerType.NETWORK:
        instancePort = Utils.derivePortOrPortRange(view.portRange)
        loadBalancerPort = Utils.derivePortOrPortRange(view.portRange)
        break
      case GoogleLoadBalancerType.HTTP:
        instancePort = 'http'
        loadBalancerPort = Utils.derivePortOrPortRange(view.portRange)
        break
      case GoogleLoadBalancerType.INTERNAL:
        GoogleInternalLoadBalancer.View ilbView = view as GoogleInternalLoadBalancer.View
        def portString = ilbView.ports.join(",")
        instancePort = portString
        loadBalancerPort = portString
        break
      case GoogleLoadBalancerType.SSL:
        instancePort = Utils.derivePortOrPortRange(view.portRange)
        loadBalancerPort = Utils.derivePortOrPortRange(view.portRange)
        break
      case GoogleLoadBalancerType.TCP:
        instancePort = Utils.derivePortOrPortRange(view.portRange)
        loadBalancerPort = Utils.derivePortOrPortRange(view.portRange)
        break
      default:
        throw new IllegalStateException("Load balancer ${view.name} is an unknown load balancer type.")
        break
    }
    [new GoogleLoadBalancerDetails(loadBalancerName: view.name,
                                   loadBalancerType: view.loadBalancerType,
                                   createdTime: view.createdTime,
                                   dnsname: view.ipAddress,
                                   ipAddress: view.ipAddress,
                                   healthCheck: (view.hasProperty("healthCheck") && view.healthCheck) ? view.healthCheck : null,
                                   backendServiceHealthChecks: backendServiceHealthChecks ?: null,
                                   listenerDescriptions: [[
                                                              listener: new ListenerDescription(
                                                                  instancePort: instancePort,
                                                                  loadBalancerPort: loadBalancerPort,
                                                                  instanceProtocol: view.ipProtocol,
                                                                  protocol: view.ipProtocol
                                                              )
                                                          ]])]
  }

  static class GoogleLoadBalancerAccountRegionSummary implements LoadBalancerProvider.Item {

    String name

    @JsonIgnore
    Map<String, GoogleLoadBalancerAccount> mappedAccounts = [:].withDefault {
      String accountName -> new GoogleLoadBalancerAccount(name: accountName)
    }

    @JsonProperty("accounts")
    List<GoogleLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class GoogleLoadBalancerAccount implements LoadBalancerProvider.ByAccount {

    String name

    @JsonIgnore
    Map<String, GoogleLoadBalancerAccountRegion> mappedRegions = [:].withDefault {
      String region -> new GoogleLoadBalancerAccountRegion(name: region)
    }

    @JsonProperty("regions")
    List<GoogleLoadBalancerAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }
  }

  static class GoogleLoadBalancerAccountRegion implements LoadBalancerProvider.ByRegion {
    String name
    List<GoogleLoadBalancerSummary> loadBalancers = []
  }

  static class GoogleLoadBalancerSummary implements LoadBalancerProvider.Details {
    GoogleLoadBalancerType loadBalancerType
    String account
    String region
    String name
    String type = GoogleCloudProvider.ID
    List<String> backendServices
    String urlMapName
  }

  static class GoogleLoadBalancerDetails implements LoadBalancerProvider.Details {
    Long createdTime
    String dnsname
    String ipAddress
    String loadBalancerName
    GoogleLoadBalancerType loadBalancerType
    GoogleHealthCheck.View healthCheck
    Map<String, GoogleHealthCheck.View> backendServiceHealthChecks = [:]
    // TODO(ttomsu): Bizarre nesting of data. Necessary?
    List<Map<String, ListenerDescription>> listenerDescriptions = []
  }

  static class ListenerDescription {
    String instancePort
    String instanceProtocol
    String loadBalancerPort
    String protocol
  }
}
