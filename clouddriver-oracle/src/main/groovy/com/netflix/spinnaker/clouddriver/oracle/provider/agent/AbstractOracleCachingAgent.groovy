/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ser.FilterProvider
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.http.internal.ExplicitlySetFilter

abstract class AbstractOracleCachingAgent implements CachingAgent {

  final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}
  final String clouddriverUserAgentApplicationName // "Spinnaker/${version}" HTTP header string see CloudDriverConfig
  final OracleNamedAccountCredentials credentials
  final ObjectMapper objectMapper
  final String providerName = OracleCloudProvider.ID
  final String agentType


  AbstractOracleCachingAgent(ObjectMapper objectMapper, OracleNamedAccountCredentials credentials, String clouddriverUserAgentApplicationName) {
    this.credentials = credentials
    this.clouddriverUserAgentApplicationName = clouddriverUserAgentApplicationName
    agentType = "${credentials.name}/${credentials.region}/${this.class.simpleName}"
    
    FilterProvider filters = new SimpleFilterProvider().setFailOnUnknownId(false)
    //Alternatives of adding explicitlySetFilter:
    //- FilterProvider filters = new SimpleFilterProvider().addFilter("explicitlySetFilter", (SimpleBeanPropertyFilter) SimpleBeanPropertyFilter.serializeAllExcept(['__explicitlySet__'].toSet()));
    //- FilterProvider filters = new SimpleFilterProvider().addFilter("explicitlySetFilter", (SimpleBeanPropertyFilter) com.oracle.bmc.http.internal.ExplicitlySetFilter.INSTANCE)
    this.objectMapper = objectMapper.setFilterProvider(filters)
  }
}
