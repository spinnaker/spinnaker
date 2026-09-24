/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.eureka.provider

import com.netflix.spinnaker.cats.provider.ProviderCacheConfiguration
import spock.lang.Specification

class EurekaCachingProviderSpec extends Specification {

  // SqlProviderRegistry only wires a provider's ProviderCacheConfiguration into its SqlCache when
  // the provider implements the interface; otherwise SqlCache falls back to a default that
  // disables full eviction, and EurekaCachingAgent's always-present-key CacheResult is silently
  // discarded before its eviction diff can run. A regression here would reintroduce the
  // stale-cache bug for every Eureka-backed account on the SQL cache backend.
  def "supports full eviction"() {
    given:
    def provider = new EurekaCachingProvider([])

    expect:
    provider instanceof ProviderCacheConfiguration
    provider.supportsFullEviction()
  }
}
