/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials

abstract class AbstractOracleBMCSCachingAgent implements CachingAgent {

  final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}
  final String clouddriverUserAgentApplicationName // "Spinnaker/${version}" HTTP header string
  final OracleBMCSNamedAccountCredentials credentials
  final ObjectMapper objectMapper
  final String providerName = OracleBMCSCloudProvider.ID
  final String agentType


  AbstractOracleBMCSCachingAgent(ObjectMapper objectMapper, OracleBMCSNamedAccountCredentials credentials, String clouddriverUserAgentApplicationName) {
    this.objectMapper = objectMapper
    this.credentials = credentials
    this.clouddriverUserAgentApplicationName = clouddriverUserAgentApplicationName
    agentType = "${credentials.name}/${credentials.region}/${this.class.simpleName}"
  }
}
