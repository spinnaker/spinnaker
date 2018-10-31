/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.model.OracleSubnet
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class OracleLoadBalancerProvider implements LoadBalancerProvider<OracleLoadBalancerDetail> {

  final Cache cacheView
  final ObjectMapper objectMapper
  final String cloudProvider = OracleCloudProvider.ID

  @Autowired
  OracleSubnetProvider oracleSubnetProvider;

  @Autowired
  OracleLoadBalancerProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  List<LoadBalancerProvider.Item> list() {
    def results = []
    getAll().each { lb ->
      def summary = new OracleLoadBalancerSummary(name: lb.name)
      summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << lb
      results << summary
    }

    return results
  }

  @Override
  LoadBalancerProvider.Item get(String name) {
    def summary = new OracleLoadBalancerSummary(name: name)
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(name, '*', '*', '*')).each { lb ->
      summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << lb
    }
    return summary
  }

  @Override
  List<LoadBalancerProvider.Details> byAccountAndRegionAndName(String account, String region, String name) {
    return getAllMatchingKeyPattern(Keys.getLoadBalancerKey(name, '*', region, account))?.toList()
  }

  @Override
  Set<OracleLoadBalancerDetail> getApplicationLoadBalancers(String application) {
    return getAllMatchingKeyPattern(Keys.getLoadBalancerKey("$application*", '*', '*', '*'))
  }

  Set<OracleLoadBalancerDetail> getAll() {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey('*', '*', '*', '*'))
  }

  Set<OracleLoadBalancerDetail> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(Keys.Namespace.LOADBALANCERS.ns, pattern))
  }

  Set<OracleLoadBalancerDetail> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(Keys.Namespace.LOADBALANCERS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)

    return transformed
  }

  OracleLoadBalancerDetail fromCacheData(CacheData cacheData) {
    LoadBalancer loadBalancer = objectMapper.convertValue(cacheData.attributes, LoadBalancer)
    Map<String, String> parts = Keys.parse(cacheData.id)
    Set<OracleSubnet> subnets = loadBalancer.subnetIds?.collect {
      oracleSubnetProvider.getAllMatchingKeyPattern(Keys.getSubnetKey(it, parts.region, parts.account))
    }.flatten();
    return new OracleLoadBalancerDetail(
      id: loadBalancer.id,
      name: loadBalancer.displayName,
      account: parts.account,
      region: parts.region,
      ipAddresses: loadBalancer.ipAddresses,
      listeners: loadBalancer.listeners,
      backendSets: loadBalancer.backendSets,
      subnets: subnets,
      timeCreated: loadBalancer.timeCreated.toInstant().toString(),
      serverGroups: [] as Set<LoadBalancerServerGroup>)
  }

  static class OracleLoadBalancerSummary implements LoadBalancerProvider.Item {

    private Map<String, OracleLoadBalancerAccount> mappedAccounts = [:]
    String name

    OracleLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new OracleLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<OracleLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class OracleLoadBalancerAccount implements LoadBalancerProvider.ByAccount {

    private Map<String, OracleLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    OracleLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new OracleLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<OracleLoadBalancerAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }
  }

  static class OracleLoadBalancerAccountRegion implements LoadBalancerProvider.ByRegion {

    String name
    List<OracleLoadBalancerSummary> loadBalancers
  }

  static class OracleLoadBalancerDetail implements LoadBalancerProvider.Details, com.netflix.spinnaker.clouddriver.model.LoadBalancer {

    String account
    String region
    String name
    String type = 'oracle'
    String loadBalancerType = 'oci'
    String cloudProvider = 'oracle'
    String id
    String timeCreated
    Set<LoadBalancerServerGroup> serverGroups = []
    List ipAddresses = []
    Map listeners
    Map backendSets
    Set<OracleSubnet> subnets
  }

}
