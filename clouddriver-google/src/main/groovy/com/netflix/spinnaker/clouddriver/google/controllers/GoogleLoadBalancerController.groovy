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

package com.netflix.spinnaker.clouddriver.google.controllers

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProviderTempShim
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/gce/loadBalancers")
class GoogleLoadBalancerController implements LoadBalancerProviderTempShim {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

  @RequestMapping(method = RequestMethod.GET)
  List<GoogleLoadBalancerAccountRegionSummary> list() {
    def loadBalancerViewsByName = googleLoadBalancerProvider.getApplicationLoadBalancers("").groupBy { it.name }

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

  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  GoogleLoadBalancerAccountRegionSummary get(@PathVariable String name) {
    // TODO(ttomsu): It's inefficient to pull everything back and (possibly) discard most of it.
    // Refactor when addressing https://github.com/spinnaker/spinnaker/issues/807
    list().find { it.name == name }
  }

  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  List<GoogleLoadBalancerDetails> byAccountAndRegionAndName(@PathVariable String account,
                                                            @PathVariable String region,
                                                            @PathVariable String name) {
    GoogleLoadBalancerView view = googleLoadBalancerProvider.getApplicationLoadBalancers(name).find { view ->
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
        instancePort = 'http' // NOTE: This is what occurs in Google Cloud Console, it's not documented and a bit non-sensical.
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
                                   healthCheck: view.healthCheck ?: null,
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

  static class GoogleLoadBalancerAccountRegionSummary implements LoadBalancerProviderTempShim.Item {

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

  static class GoogleLoadBalancerAccount implements LoadBalancerProviderTempShim.ByAccount {

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

  static class GoogleLoadBalancerAccountRegion implements LoadBalancerProviderTempShim.ByRegion {
    String name
    List<GoogleLoadBalancerSummary> loadBalancers = []
  }

  static class GoogleLoadBalancerSummary implements LoadBalancerProviderTempShim.Details {
    GoogleLoadBalancerType loadBalancerType
    String account
    String region
    String name
    String type = GoogleCloudProvider.GCE
    List<String> backendServices
    String urlMapName
  }

  static class GoogleLoadBalancerDetails implements LoadBalancerProviderTempShim.Details {
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
