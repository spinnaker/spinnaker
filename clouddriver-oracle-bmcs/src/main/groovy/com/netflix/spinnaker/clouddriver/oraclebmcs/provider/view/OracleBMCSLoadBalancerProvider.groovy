/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class OracleBMCSLoadBalancerProvider implements LoadBalancerProvider<OBMCSLoadBalancerDetail> {

  final Cache cacheView
  final ObjectMapper objectMapper
  final String cloudProvider = OracleBMCSCloudProvider.ID

  @Autowired
  OracleBMCSLoadBalancerProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  List<LoadBalancerProvider.Item> list() {
    def results = []
    getAll().each { lb ->
      def summary = new OBMCSLoadBalancerSummary(name: lb.name)
      summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << lb
      results << summary
    }

    return results
  }

  @Override
  LoadBalancerProvider.Item get(String name) {
    def summary = new OBMCSLoadBalancerSummary(name: name)
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(name, '*', '*', '*')).each { lb ->
      summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << lb
    }
    return summary
  }

  @Override
  List<LoadBalancerProvider.Details> byAccountAndRegionAndName(String account, String region, String name) {
    return getAllMatchingKeyPattern(Keys.getLoadBalancerKey(name, '*', region, account))
  }

  @Override
  Set<OBMCSLoadBalancerDetail> getApplicationLoadBalancers(String application) {
    return getAllMatchingKeyPattern(Keys.getLoadBalancerKey("$application*", '*', '*', '*'))
  }

  Set<OBMCSLoadBalancerDetail> getAll() {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey('*', '*', '*', '*'))
  }

  Set<OBMCSLoadBalancerDetail> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(Keys.Namespace.LOADBALANCERS.ns, pattern))
  }

  Set<OBMCSLoadBalancerDetail> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(Keys.Namespace.LOADBALANCERS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)

    return transformed
  }

  OBMCSLoadBalancerDetail fromCacheData(CacheData cacheData) {
    LoadBalancer loadBalancer = objectMapper.convertValue(cacheData.attributes, LoadBalancer)
    Map<String, String> parts = Keys.parse(cacheData.id)

    return new OBMCSLoadBalancerDetail(
      id: loadBalancer.id,
      name: loadBalancer.displayName,
      account: parts.account,
      region: parts.region,
      serverGroups: [loadBalancer.listeners.values().first().defaultBackendSetName] as Set<String>
    )
  }

  static class OBMCSLoadBalancerSummary implements LoadBalancerProvider.Item {

    private Map<String, OBMCSLoadBalancerAccount> mappedAccounts = [:]
    String name

    OBMCSLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new OBMCSLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<OBMCSLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class OBMCSLoadBalancerAccount implements LoadBalancerProvider.ByAccount {

    private Map<String, OBMCSLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    OBMCSLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new OBMCSLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<OBMCSLoadBalancerAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }
  }

  static class OBMCSLoadBalancerAccountRegion implements LoadBalancerProvider.ByRegion {

    String name
    List<OBMCSLoadBalancerSummary> loadBalancers
  }

  static class OBMCSLoadBalancerDetail implements LoadBalancerProvider.Details, com.netflix.spinnaker.clouddriver.model.LoadBalancer {

    String account
    String region
    String name
    String type = 'oraclebmcs'
    String cloudProvider = 'oraclebmcs'
    String id
    Set<String> serverGroups
  }

}
