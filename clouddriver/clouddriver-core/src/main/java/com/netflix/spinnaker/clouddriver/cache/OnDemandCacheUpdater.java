/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.clouddriver.cache;

import java.util.Collection;
import java.util.Map;

/**
 * An on-demand cache updater. Allows some non-scheduled trigger to initiate a cache refresh for a
 * given type. An on-demand cache request will fan-out to all available updaters.
 */
public interface OnDemandCacheUpdater {

  /**
   * Indicates if the updater is able to handle this on-demand request given the type and
   * cloudProvider
   *
   * @param type
   * @param cloudProvider
   * @return
   */
  boolean handles(OnDemandType type, String cloudProvider);

  /**
   * Handles the update request
   *
   * @param type
   * @param cloudProvider
   * @param data
   */
  OnDemandCacheResult handle(OnDemandType type, String cloudProvider, Map<String, ?> data);

  Collection<Map<String, Object>> pendingOnDemandRequests(OnDemandType type, String cloudProvider);

  Map<String, Object> pendingOnDemandRequest(OnDemandType type, String cloudProvider, String id);
}
