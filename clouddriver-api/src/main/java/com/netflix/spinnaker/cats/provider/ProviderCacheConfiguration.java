/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.cats.provider;

/** Allows cache configuration to be overridden per-provider. */
public interface ProviderCacheConfiguration {
  /**
   * By default a safe guard exists preventing removal of the last cached resource, even if it no
   * longer exists in the underlying provider.
   *
   * <p>This allows a provider to opt out of this safe guard and allow full cache eviction.
   *
   * @return true if provider cache should support full eviction, false otherwise.
   */
  default boolean supportsFullEviction() {
    return false;
  }
}
