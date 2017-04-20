/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace

class OracleBMCSInfrastructureProvider extends AgentSchedulerAware implements SearchableProvider {

  final Collection<Agent> agents

  final Set<String> defaultCaches = [
    Namespace.NETWORKS.ns,
    Namespace.SUBNETS.ns,
    Namespace.IMAGES.ns,
    Namespace.INSTANCES.ns,
    Namespace.SECURITY_GROUPS.ns,
    Namespace.SERVER_GROUPS.ns
  ].asImmutable()

  final Map<String, String> urlMappingTemplates = [:]

  final Map<SearchableProvider.SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators = Collections.emptyMap()

  final String providerName = OracleBMCSCloudProvider.ID

  OracleBMCSInfrastructureProvider(Collection<Agent> agents) {
    this.agents = agents
  }

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }
}
