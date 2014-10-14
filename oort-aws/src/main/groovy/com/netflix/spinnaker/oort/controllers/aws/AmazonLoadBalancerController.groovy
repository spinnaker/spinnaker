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

//@CompileStatic
//@RestController
//@RequestMapping("/aws/loadBalancers")
class AmazonLoadBalancerController {
//
//  @Autowired
//  CacheService cacheService
//
//  @RequestMapping(method = RequestMethod.GET)
//  List<AmazonLoadBalancerSummary> list() {
//    def loadBalancerKeys = cacheService.keysByType(Keys.Namespace.LOAD_BALANCERS)
//    def serverGroupKeys = cacheService.keysByType(Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS)
//    getSummaryForKeys(loadBalancerKeys, groupKeysByLoadBalancer(serverGroupKeys)).values() as List
//  }
//
//  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
//  AmazonLoadBalancerSummary get(@PathVariable String name) {
//    def loadBalancerKeys = cacheService.keysByType(Keys.Namespace.LOAD_BALANCERS).findAll { key ->
//      def parts = Keys.parse(key)
//      parts.loadBalancer == name
//    }
//    def serverGroupKeys = cacheService.keysByType(Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS).findAll { key ->
//      def parts = Keys.parse(key)
//      parts.loadBalancer == name
//    }
//
//    getSummaryForKeys(loadBalancerKeys, groupKeysByLoadBalancer(serverGroupKeys))?.get(name)
//  }
//
//  private Map<String, List<String>> groupKeysByLoadBalancer(Collection<String> keys) {
//    def entries = keys.groupBy { String key ->
//      key.substring(0, key.lastIndexOf(':'))
//    }
//    entries.each {
//      it.value = it.value.collect { String key ->
//        key.substring(key.lastIndexOf(':') + 1).toString() }
//    }
//  }
//
//  private Map<String, AmazonLoadBalancerSummary> getSummaryForKeys(Collection<String> loadBalancerKeys, Map<String, List<String>> summaries) {
//    Map<String, AmazonLoadBalancerSummary> map = [:]
//    for (entry in loadBalancerKeys) {
//      def parts = Keys.parse(entry)
//      String name = parts.loadBalancer
//      String region = parts.region
//      String account = parts.account
//      def summary = map.get(name)
//      if (!summary) {
//        summary = new AmazonLoadBalancerSummary(name: name)
//        map.put name, summary
//      }
//      def loadBalancer = new AmazonLoadBalancer(name, region)
//      loadBalancer.elb = cacheService.retrieve(entry, LoadBalancerDescription)
//      summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancer
//    }
//    for (entry in summaries.entrySet()) {
//      def parts = entry.key.split(':')
//      def name = parts[1]
//      def account = parts[3]
//      def region = parts[4]
//      def summary = map.get(name)
//      if (summary) {
//        def loadBalancer = summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers.find { loadBalancer ->
//          loadBalancer.getName() == name
//        }
//        if (loadBalancer) {
//          loadBalancer.serverGroups.addAll(entry.value)
//        }
//      }
//    }
//    map
//  }
//
//  // view models...
//
//  static class AmazonLoadBalancerSummary {
//    private Map<String, AmazonLoadBalancerAccount> mappedAccounts = [:]
//    String name
//
//    AmazonLoadBalancerAccount getOrCreateAccount(String name) {
//      if (!mappedAccounts.containsKey(name)) {
//        mappedAccounts.put(name, new AmazonLoadBalancerAccount(name: name))
//      }
//      mappedAccounts[name]
//    }
//
//    @JsonProperty("accounts")
//    List<AmazonLoadBalancerAccount> getAccounts() {
//      mappedAccounts.values() as List
//    }
//  }
//
//  static class AmazonLoadBalancerAccount {
//    private Map<String, AmazonLoadBalancerAccountRegion> mappedRegions = [:]
//    String name
//
//    AmazonLoadBalancerAccountRegion getOrCreateRegion(String name) {
//      if (!mappedRegions.containsKey(name)) {
//        mappedRegions.put(name, new AmazonLoadBalancerAccountRegion(name: name, loadBalancers: []))
//      }
//      mappedRegions[name]
//    }
//
//    @JsonProperty("regions")
//    List<AmazonLoadBalancerAccountRegion> getRegions() {
//      mappedRegions.values() as List
//    }
//  }
//
//  static class AmazonLoadBalancerAccountRegion {
//    String name
//    List<AmazonLoadBalancer> loadBalancers
//  }
}
