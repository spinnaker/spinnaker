/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache

interface OnDemandAgent {

  String getProviderName()

  String getOnDemandAgentType()

  // TODO(ttomsu): This seems like it should go in a different interface.
  OnDemandMetricsSupport getMetricsSupport()

  enum OnDemandType {
    ServerGroup,
    SecurityGroup,
    LoadBalancer,
    Job,
    TargetGroup

    static OnDemandType fromString(String s) {
      OnDemandType t = values().find { it.toString().equalsIgnoreCase(s) }
      if (!t) {
        throw new IllegalArgumentException("Cannot create OnDemandType from String '${s}'")
      }
      return t
    }
  }

  boolean handles(OnDemandType type, String cloudProvider)

  static class OnDemandResult {
    String sourceAgentType
    Collection<String> authoritativeTypes = []
    CacheResult cacheResult
    Map<String, Collection<String>> evictions = [:]
  }

  OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data)

  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache)
}
