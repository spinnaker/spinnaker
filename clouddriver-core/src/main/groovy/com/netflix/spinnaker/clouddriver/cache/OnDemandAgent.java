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

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.moniker.Moniker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public interface OnDemandAgent {
  String getProviderName();

  String getOnDemandAgentType();

  // TODO(ttomsu): This seems like it should go in a different interface.
  OnDemandMetricsSupport getMetricsSupport();

  enum OnDemandType {
    ServerGroup,
    SecurityGroup,
    LoadBalancer,
    Job,
    TargetGroup;

    static OnDemandType fromString(String s) {
      return Arrays.stream(values())
          .filter(v -> v.toString().equalsIgnoreCase(s))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Cannot create OnDemandType from '" + s + "'"));
    }
  }

  boolean handles(OnDemandType type, String cloudProvider);

  static class OnDemandResult {
    String sourceAgentType;
    Collection<String> authoritativeTypes = new ArrayList<>();
    CacheResult cacheResult;
    Map<String, Collection<String>> evictions = new HashMap<>();

    public OnDemandResult() {}

    public OnDemandResult(String sourceAgentType, CacheResult cacheResult, Map<String, Collection<String>> evictions) {
      this.sourceAgentType = sourceAgentType;
      this.cacheResult = cacheResult;
      this.evictions = evictions;
    }
  }

  /*
   * WARNING: this is an interim solution while cloud providers write their own ways to derive monikers.
   */
  default Moniker convertOnDemandDetails(Map<String, String> details) {
    if (details == null || details.isEmpty()) {
      return null;
    }

    try {
      return Moniker.builder()
          .app(details.get("application"))
          .stack(details.get("stack"))
          .detail(details.get("detail"))
          .cluster(details.get("cluster"))
          .sequence(Integer.valueOf(details.get("sequence")))
          .build();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data);
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache);
}
