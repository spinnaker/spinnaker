/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.gate.ratelimit;

import com.netflix.spinnaker.gate.config.RateLimiterConfiguration.PrincipalOverride;

import java.util.List;

abstract public class AbstractRateLimitPrincipalProvider implements RateLimitPrincipalProvider {

  boolean isLearning(String name, List<String> enforcing, List<String> ignoring, boolean globalLearningFlag) {
    return !enforcing.contains(name) && (ignoring.contains(name) || globalLearningFlag);
  }

  Integer overrideOrDefault(String principal, List<PrincipalOverride> overrides, Integer defaultValue) {
    return overrides.stream()
      .filter(o -> principal.equals(o.getPrincipal()))
      .map(PrincipalOverride::getOverride)
      .findFirst()
      .orElse(defaultValue);
  }
}
