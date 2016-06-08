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
import com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/gce/loadBalancers")
class GoogleLoadBalancerController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

  @RequestMapping(method = RequestMethod.GET)
  List<GoogleLoadBalancerAccountRegionSummary> list() {
    def loadBalancerViewsByName = googleLoadBalancerProvider.getApplicationLoadBalancers("").groupBy { it.name }

    loadBalancerViewsByName.collect { String name, List<GoogleLoadBalancer.View> views ->
      def summary = new GoogleLoadBalancerAccountRegionSummary(name: name)

      views.each { GoogleLoadBalancer.View view ->
        summary.mappedAccounts[view.account].mappedRegions[view.region].loadBalancers << new GoogleLoadBalancerSummary(
            account: view.account,
            region: view.region,
            name: view.name)
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
  List<GoogleLoadBalancerDetails> getDetailsInAccountAndRegionByName(@PathVariable String account,
                                                                     @PathVariable String region,
                                                                     @PathVariable String name) {
    GoogleLoadBalancer.View view = googleLoadBalancerProvider.getApplicationLoadBalancers(name).find { view ->
      view.account == account && view.region == region
    }

    if (!view) {
      return []
    }

    [new GoogleLoadBalancerDetails(loadBalancerName: view.name,
                                   createdTime: view.createdTime,
                                   dnsname: view.ipAddress,
                                   ipAddress: view.ipAddress,
                                   healthCheck: view.healthCheck,
                                   listenerDescriptions: [[
                                       listener: new ListenerDescription(instancePort: view.portRange,
                                                                         loadBalancerPort: view.portRange,
                                                                         instanceProtocol: view.ipProtocol,
                                                                         protocol: view.ipProtocol)
                                   ]])]
  }

  static class GoogleLoadBalancerAccountRegionSummary {

    String name

    @JsonIgnore
    Map<String, GoogleLoadBalancerAccount> mappedAccounts = [:].withDefault {
      String accountName -> new GoogleLoadBalancerAccount(name: accountName)
    }

    @JsonProperty("accounts")
    List<GoogleLoadBalancerAccount> getAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class GoogleLoadBalancerAccount {

    String name

    @JsonIgnore
    Map<String, GoogleLoadBalancerAccountRegion> mappedRegions = [:].withDefault {
      String region -> new GoogleLoadBalancerAccountRegion(name: region)
    }

    @JsonProperty("regions")
    List<GoogleLoadBalancerAccountRegion> getRegions() {
      mappedRegions.values() as List
    }
  }

  static class GoogleLoadBalancerAccountRegion {
    String name
    List<GoogleLoadBalancerSummary> loadBalancers = []
  }

  static class GoogleLoadBalancerSummary {
    String account
    String region
    String name
    String type = GoogleCloudProvider.GCE
  }

  static class GoogleLoadBalancerDetails {
    Long createdTime
    String dnsname
    String ipAddress
    String loadBalancerName
    GoogleHealthCheck.View healthCheck
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
