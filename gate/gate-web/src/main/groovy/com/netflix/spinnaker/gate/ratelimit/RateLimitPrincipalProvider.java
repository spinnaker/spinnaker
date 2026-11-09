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

import java.util.Collection;
import java.util.function.Supplier;

public interface RateLimitPrincipalProvider {

  /** The shared principal for every unauthenticated caller. */
  String ANONYMOUS = "anonymous";

  RateLimitPrincipal getPrincipal(String name, String sourceApp);

  /**
   * @param roles supplies the caller's roles. Looking roles up can be expensive, so implementations
   *     should only call it when roles can influence the result.
   */
  default RateLimitPrincipal getPrincipal(
      String name, String sourceApp, Supplier<Collection<String>> roles) {
    return getPrincipal(name, sourceApp);
  }
}
