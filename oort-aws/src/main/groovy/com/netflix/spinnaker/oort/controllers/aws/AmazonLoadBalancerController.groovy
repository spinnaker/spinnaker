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

package com.netflix.spinnaker.oort.controllers.aws

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.config.AwsConfigurationProperties
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.SERVER_GROUPS

@RestController
@RequestMapping("/aws/loadBalancers")
class AmazonLoadBalancerController {

  private final Cache cacheView
  private final AwsConfigurationProperties awsConfigurationProperties

  @Autowired
  AmazonLoadBalancerController(Cache cacheView, AwsConfigurationProperties awsConfigurationProperties) {
    this.cacheView = cacheView
    this.awsConfigurationProperties = awsConfigurationProperties
  }

  @RequestMapping(method = RequestMethod.GET)
  List<AmazonLoadBalancerSummary> list() {
    Collection<CacheData> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns)
    getSummaryForLoadBalancers(loadBalancers).values() as List
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
  AmazonLoadBalancerSummary get(@PathVariable String name) {
    Collection<CacheData> loadBalancers = awsConfigurationProperties.accounts.findResults { NetflixAssumeRoleAmazonCredentials account ->
      Collection<CacheData> regionLbs = account.regions.findResults { AmazonCredentials.AWSRegion region ->
        cacheView.get(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey(name, account.name, region.name))
      }
      regionLbs
    }.flatten()

    getSummaryForLoadBalancers(loadBalancers).get(name)
  }

  @RequestMapping(value = "/{account}/{region}", method = RequestMethod.GET)
  List<AmazonLoadBalancerSummary> getInAccountAndRegion(@PathVariable String account, @PathVariable String region) {
    Collection<String> identifiers = cacheView.getIdentifiers(LOAD_BALANCERS.ns).findAll {
      def key = Keys.parse(it)
      key.account == account && key.region == region
    }
    Collection<CacheData> loadBalancers = identifiers.collect {
      cacheView.get(LOAD_BALANCERS.ns, it)
    }

    getSummaryForLoadBalancers(loadBalancers).values() as List
  }


  private Map<String, AmazonLoadBalancerSummary> getSummaryForLoadBalancers(Collection<CacheData> loadBalancers) {
    Map<String, AmazonLoadBalancerSummary> map = [:]
    for (lb in loadBalancers) {
      def parts = Keys.parse(lb.id)
      String name = parts.loadBalancer
      String region = parts.region
      String account = parts.account
      def summary = map.get(name)
      if (!summary) {
        summary = new AmazonLoadBalancerSummary(name: name)
        map.put name, summary
      }
      def loadBalancer = new AmazonLoadBalancer(name, region)
      loadBalancer.elb = lb.attributes
      loadBalancer.getServerGroups().addAll(lb.relationships[SERVER_GROUPS.ns]?.findResults { Keys.parse(it).serverGroup } ?: [])

      summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancer
    }
    map
  }

  // view models...

  static class AmazonLoadBalancerSummary {
    private Map<String, AmazonLoadBalancerAccount> mappedAccounts = [:]
    String name

    AmazonLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new AmazonLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<AmazonLoadBalancerAccount> getAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class AmazonLoadBalancerAccount {
    private Map<String, AmazonLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    AmazonLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new AmazonLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<AmazonLoadBalancerAccountRegion> getRegions() {
      mappedRegions.values() as List
    }
  }

  static class AmazonLoadBalancerAccountRegion {
    String name
    List<AmazonLoadBalancer> loadBalancers
  }
}
