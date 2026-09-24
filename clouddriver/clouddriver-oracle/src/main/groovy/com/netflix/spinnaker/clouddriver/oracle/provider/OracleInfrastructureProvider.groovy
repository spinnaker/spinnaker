/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.cats.provider.ProviderCacheConfiguration
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys.Namespace

class OracleInfrastructureProvider extends AgentSchedulerAware implements SearchableProvider, ProviderCacheConfiguration {

  final Collection<Agent> agents

  /**
   * Every caching agent here always reports its authoritative namespace's key in the
   * CacheResult, even with an empty list when there's no live data this cycle, so the SQL
   * cache's existingIds-minus-currentIds eviction diff can always run. Without opting in here,
   * SqlCache's default safeguard against ever evicting the last item of a type discards that
   * entry before the diff can run, and the stale entry is never cleaned up.
   */
  @Override
  boolean supportsFullEviction() {
    return true
  }

  final Set<String> defaultCaches = [
    Namespace.NETWORKS.ns,
    Namespace.SUBNETS.ns,
    Namespace.IMAGES.ns,
    Namespace.INSTANCES.ns,
    Namespace.SECURITY_GROUPS.ns,
    Namespace.SERVER_GROUPS.ns,
    Namespace.LOADBALANCERS.ns
  ].asImmutable()

  final Map<String, String> urlMappingTemplates = [:]

  final Map<SearchableProvider.SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators = Collections.emptyMap()

  final String providerName = OracleCloudProvider.ID

  OracleInfrastructureProvider(Collection<Agent> agents) {
    this.agents = agents
  }

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }
}
