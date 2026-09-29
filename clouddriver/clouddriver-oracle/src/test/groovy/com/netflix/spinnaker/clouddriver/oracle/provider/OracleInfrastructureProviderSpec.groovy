/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider

import com.netflix.spinnaker.cats.provider.ProviderCacheConfiguration
import spock.lang.Specification

class OracleInfrastructureProviderSpec extends Specification {

  // SqlProviderRegistry only wires a provider's ProviderCacheConfiguration into its SqlCache when
  // the provider implements the interface; otherwise SqlCache falls back to a default that
  // disables full eviction, and every Oracle caching agent's always-present-key CacheResult is
  // silently discarded before its eviction diff can run. A regression here would reintroduce the
  // stale-cache bug for every Oracle account on the SQL cache backend.
  def "supports full eviction"() {
    given:
    def provider = new OracleInfrastructureProvider([])

    expect:
    provider instanceof ProviderCacheConfiguration
    provider.supportsFullEviction()
  }
}
