/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.provider.config

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.provider.agent.OracleInstanceCachingAgent
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.oracle.bmc.core.model.Instance
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class OracleInfrastructureProviderConfigSpec extends Specification {
  def "provider agents store creation dates as timestamps"() {
    given:
    def credentials = Stub(OracleNamedAccountCredentials) {
      getName() >> "test-account"
      getRegion() >> "us-phoenix-1"
    }
    def repository = Stub(AccountCredentialsRepository) {
      getAll() >> ([credentials] as Set)
    }
    def mapper = JsonMapper.builder().build()
    def provider = new OracleInfrastructureProviderConfig().oracleInfrastructureProvider(
      "test", repository, mapper, new NoopRegistry(), Stub(OracleServerGroupService))
    def agent = provider.agents.find { it instanceof OracleInstanceCachingAgent } as OracleInstanceCachingAgent
    def instance = Instance.builder().id("instance-id").displayName("instance")
      .timeCreated(new Date(1234)).build()

    when:
    def result = agent.buildCacheResults([instance])

    then:
    result.cacheResults[Keys.Namespace.INSTANCES.ns].first().attributes.timeCreated == 1234L
    mapper.readTree(mapper.writeValueAsString(new Date(1234))).isString()
  }
}
