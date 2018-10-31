/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.model.OracleCluster
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class OracleClusterProvider implements ClusterProvider<OracleCluster.View> {

  final String cloudProviderId = OracleCloudProvider.ID

  private OracleInstanceProvider instanceProvider
  final ObjectMapper objectMapper
  private AccountCredentialsProvider accountCredentialsProvider
  private final Cache cacheView

  @Autowired
  OracleClusterProvider(OracleInstanceProvider instanceProvider,
                            ObjectMapper objectMapper,
                            AccountCredentialsProvider accountCredentialsProvider,
                            Cache cacheView) {
    this.instanceProvider = instanceProvider
    this.objectMapper = objectMapper
    this.accountCredentialsProvider = accountCredentialsProvider
    this.cacheView = cacheView
  }

  @Override
  Map<String, Set<OracleCluster.View>> getClusters() {
    Collection<String> identifiers = cacheView.getIdentifiers(Keys.Namespace.SERVER_GROUPS.ns)
    Set<OracleServerGroup> serverGroups = loadServerGroups(identifiers)
    return clustersFromServerGroups(serverGroups).groupBy { it.accountName }
  }

  @Override
  Map<String, Set<OracleCluster.View>> getClusterSummaries(String application) {
    getClusterDetails(application)
  }

  @Override
  Map<String, Set<OracleCluster.View>> getClusterDetails(String application) {
    Collection<String> identifiers = cacheView.getIdentifiers(Keys.Namespace.SERVER_GROUPS.ns).findAll {
      application == Keys.parse(it)?.get("application")
    }
    Set<OracleServerGroup> serverGroups = loadServerGroups(identifiers)
    return clustersFromServerGroups(serverGroups).groupBy { it.accountName }
  }

  @Override
  Set<OracleCluster.View> getClusters(String application, String account) {
    getClusterDetails(application)[account]
  }

  @Override
  OracleCluster.View getCluster(String application, String account, String name, boolean includeDetails) {
    getClusters(application, account).find { name == it.name }
  }

  @Override
  OracleCluster.View getCluster(String application, String account, String name) {
    return getCluster(application, account, name, true)
  }

  @Override
  ServerGroup getServerGroup(String account, String region, String name, boolean includeDetails) {
    def pattern = Keys.getServerGroupKey(account, region, name)
    def identifiers = cacheView.filterIdentifiers(Keys.Namespace.SERVER_GROUPS.ns, pattern)
    Set<OracleServerGroup> serverGroups = loadServerGroups(identifiers)
    if (serverGroups.isEmpty()) {
      return null
    }
    return serverGroups.iterator().next().getView()
  }

  @Override
  ServerGroup getServerGroup(String account, String region, String name) {
    return getServerGroup(account, region, name, true)
  }

  @Override
  boolean supportsMinimalClusters() {
    return false
  }

  private OracleServerGroup restoreCreds(OracleServerGroup partial, String identifier) {
    def account = Keys.parse(identifier)?.get("account")
    partial.credentials = accountCredentialsProvider.getCredentials(account)
    return partial
  }

  private Set<OracleServerGroup> loadServerGroups(Collection<String> identifiers) {
    def data = cacheView.getAll(Keys.Namespace.SERVER_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    return data.collect { cacheItem ->
      def sg = objectMapper.convertValue(cacheItem.attributes, OracleServerGroup)
      restoreCreds(sg, cacheItem.id)

      sg.instances?.each {
        def instance = instanceProvider.getInstance(Keys.parse(cacheItem.id)?.get("account"), "*", it.id)
        if (instance) {
          //TODO display name with id or privateIp
          //it.name = it.name + (it.privateIp? '_' + it.privateIp : '')
          it.healthState = instance.healthState
          it.health = instance.health
          if (sg.disabled) {
            it.healthState = HealthState.OutOfService
            it.health[0].state = HealthState.OutOfService.name()
          }

        }
      }?.removeAll {
        def instance = instanceProvider.getInstance(Keys.parse(cacheItem.id)?.get("account"), "*", it.id)
        return instance == null
      }

      return sg
    }
  }

  private String accountFromServerGroups(List<OracleServerGroup> sgs) {
    sgs?.iterator()?.next()?.credentials?.name
  }

  private Set<OracleCluster.View> clustersFromServerGroups(Set<OracleServerGroup> serverGroups) {
    Map<String, List<OracleServerGroup>> byClusterName = serverGroups.groupBy {
      Names.parseName(it.name).cluster
    }

    return byClusterName.collect { k, v ->
      new OracleCluster(
        name: k,
        accountName: accountFromServerGroups(v),
        serverGroups: v as Set<OracleServerGroup>
      ).getView()
    }
  }

}
