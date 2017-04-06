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
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSInstance
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.oracle.bmc.core.model.Instance
import groovy.util.logging.Slf4j
import org.apache.commons.lang.NotImplementedException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.INSTANCES

@Slf4j
@Component
class OracleBMCSInstanceProvider implements InstanceProvider<OracleBMCSInstance> {

  private final Cache cacheView
  final ObjectMapper objectMapper
  final AccountCredentialsProvider accountCredentialsProvider
  final String cloudProvider = OracleBMCSCloudProvider.ID

  @Autowired
  OracleBMCSInstanceProvider(Cache cacheView, ObjectMapper objectMapper, AccountCredentialsProvider accountCredentialsProvider) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
    this.accountCredentialsProvider = accountCredentialsProvider
  }

  @Override
  OracleBMCSInstance getInstance(String account, String region, String nameOrOcid) {
    def cacheKeyWithOcid = Keys.getInstanceKey(account, region, "*", nameOrOcid)
    def cacheKeyWithName = Keys.getInstanceKey(account, region, nameOrOcid, "*")
    def identifiers = cacheView.filterIdentifiers(INSTANCES.ns, cacheKeyWithOcid)
    identifiers.addAll(cacheView.filterIdentifiers(INSTANCES.ns, cacheKeyWithName))
    if (identifiers.size() > 1) {
      log.warn("There should be at most one identifier!")
    }
    Set<OracleBMCSInstance> instances = loadInstances(identifiers)
    return instances.first()
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    // TODO: Add this when we actually need it in Deck
    throw new NotImplementedException()
  }

  private Set<OracleBMCSInstance> loadInstances(Collection<String> identifiers) {
    def data = cacheView.getAll(INSTANCES.ns, identifiers, RelationshipCacheFilter.none())
    return data.collect(this.&fromCacheData)
  }

  private OracleBMCSInstance fromCacheData(CacheData cacheData) {
    Instance instance = objectMapper.convertValue(cacheData.attributes, Instance)
    Map<String, String> parts = Keys.parse(cacheData.id)
    new OracleBMCSInstance(
      name: instance.displayName,
      healthState: getHealthState(instance.lifecycleState),
      launchTime: instance.timeCreated.getTime(),
      zone: instance.availabilityDomain,
      health: [["state"      : getHealthState(instance.lifecycleState).name(),
                "type"       : "Oracle",
                "healthClass": "platform"]],
      providerType: this.cloudProvider,
      cloudProvider: this.cloudProvider,
      account: parts.account,
      region: parts.region,
      id: parts.id
    )
  }

  private HealthState getHealthState(Instance.LifecycleState lifecycleState) {
    switch (lifecycleState) {
      case Instance.LifecycleState.Provisioning:
        return HealthState.Starting
      case Instance.LifecycleState.Running:
        return HealthState.Up
      case Instance.LifecycleState.Starting:
        return HealthState.Starting
      case Instance.LifecycleState.Stopping:
        return HealthState.Down
      case Instance.LifecycleState.Stopped:
        return HealthState.Down
      case Instance.LifecycleState.CreatingImage:
        return HealthState.Starting
      case Instance.LifecycleState.Terminating:
        return HealthState.Down
      case Instance.LifecycleState.Terminated:
        return HealthState.Down
    }
  }

}
