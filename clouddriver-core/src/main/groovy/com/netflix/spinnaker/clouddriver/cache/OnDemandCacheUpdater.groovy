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
 * @author Dan Woods
 */
interface OnDemandCacheUpdater {

  /**
   * Indicates if the updater is able to handle this on-demand request.
   *
   * @param type
   * @return
   */
  boolean handles(String type)

  /**
   * The input for the updater to process this request.
   *
   * @param data
   */
  void handle(String type, Map<String, ? extends Object> data)
}
