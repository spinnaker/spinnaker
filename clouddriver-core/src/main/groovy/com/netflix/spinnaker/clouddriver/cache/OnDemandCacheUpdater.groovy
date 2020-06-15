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

/**
 * An on-demand cache updater. Allows some non-scheduled trigger to initiate a cache refresh for a given type. An on-demand cache request will fan-out to all available updaters.
 *
 *
 */
interface OnDemandCacheUpdater {

  enum OnDemandCacheStatus {
    SUCCESSFUL,
    PENDING
  }

  /**
   * Indicates if the updater is able to handle this on-demand request given the type and cloudProvider
   * @param type
   * @param cloudProvider
   * @return
   */
  boolean handles(OnDemandType type, String cloudProvider)

  /**
   * Handles the update request
   * @param type
   * @param cloudProvider
   * @param data
   */
  OnDemandCacheResult handle(OnDemandType type, String cloudProvider, Map<String, ? extends Object> data)

  Collection<Map> pendingOnDemandRequests(OnDemandType type, String cloudProvider)

  Map pendingOnDemandRequest(OnDemandType type, String cloudProvider, String id)

  static class OnDemandCacheResult {
    OnDemandCacheStatus status
    Map<String, List<String>> cachedIdentifiersByType = [:]
  }
}
