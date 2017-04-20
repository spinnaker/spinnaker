/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSCluster
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class OracleBMCSClusterProvider implements ClusterProvider<OracleBMCSCluster.View> {

  final String cloudProviderId = OracleBMCSCloudProvider.ID

  private OracleBMCSInstanceProvider instanceProvider
  final ObjectMapper objectMapper
  private AccountCredentialsProvider accountCredentialsProvider
  private final Cache cacheView

  @Autowired
  OracleBMCSClusterProvider(OracleBMCSInstanceProvider instanceProvider,
                            ObjectMapper objectMapper,
                            AccountCredentialsProvider accountCredentialsProvider,
                            Cache cacheView) {
    this.instanceProvider = instanceProvider
    this.objectMapper = objectMapper
    this.accountCredentialsProvider = accountCredentialsProvider
    this.cacheView = cacheView
  }

  @Override
  Map<String, Set<OracleBMCSCluster.View>> getClusters() {
    Collection<String> identifiers = cacheView.getIdentifiers(Keys.Namespace.SERVER_GROUPS.ns)
    Set<OracleBMCSServerGroup> serverGroups = loadServerGroups(identifiers)
    return clustersFromServerGroups(serverGroups).groupBy { it.accountName }
  }

  @Override
  Map<String, Set<OracleBMCSCluster.View>> getClusterSummaries(String application) {
    getClusterDetails(application)
  }

  @Override
  Map<String, Set<OracleBMCSCluster.View>> getClusterDetails(String application) {
    Collection<String> identifiers = cacheView.getIdentifiers(Keys.Namespace.SERVER_GROUPS.ns).findAll {
      application == Keys.parse(it)?.get("application")
    }
    Set<OracleBMCSServerGroup> serverGroups = loadServerGroups(identifiers)
    return clustersFromServerGroups(serverGroups).groupBy { it.accountName }
  }

  @Override
  Set<OracleBMCSCluster.View> getClusters(String application, String account) {
    getClusterDetails(application)[account]
  }

  @Override
  OracleBMCSCluster.View getCluster(String application, String account, String name) {
    getClusters(application, account).find { name == it.name }
  }

  @Override
  ServerGroup getServerGroup(String account, String region, String name) {
    def pattern = Keys.getServerGroupKey(account, region, name)
    def identifiers = cacheView.filterIdentifiers(Keys.Namespace.SERVER_GROUPS.ns, pattern)
    Set<OracleBMCSServerGroup> serverGroups = loadServerGroups(identifiers)
    if (serverGroups.isEmpty()) {
      return null
    }
    return serverGroups.iterator().next().getView()
  }

  private OracleBMCSServerGroup restoreCreds(OracleBMCSServerGroup partial, String identifier) {
    def account = Keys.parse(identifier)?.get("account")
    partial.credentials = accountCredentialsProvider.getCredentials(account)
    return partial
  }

  private Set<OracleBMCSServerGroup> loadServerGroups(Collection<String> identifiers) {
    def data = cacheView.getAll(Keys.Namespace.SERVER_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    return data.collect { cacheItem ->
      def sg = objectMapper.convertValue(cacheItem.attributes, OracleBMCSServerGroup)
      restoreCreds(sg, cacheItem.id)

      sg.instances?.each {
        def instance = instanceProvider.getInstance(Keys.parse(cacheItem.id)?.get("account"), "*", it.id)
        if (instance) {
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

  private String accountFromServerGroups(List<OracleBMCSServerGroup> sgs) {
    sgs?.iterator()?.next()?.credentials?.name
  }

  private Set<OracleBMCSCluster.View> clustersFromServerGroups(Set<OracleBMCSServerGroup> serverGroups) {
    Map<String, List<OracleBMCSServerGroup>> byClusterName = serverGroups.groupBy {
      Names.parseName(it.name).cluster
    }

    return byClusterName.collect { k, v ->
      new OracleBMCSCluster(
        name: k,
        accountName: accountFromServerGroups(v),
        serverGroups: v as Set<OracleBMCSServerGroup>
      ).getView()
    }
  }

}
