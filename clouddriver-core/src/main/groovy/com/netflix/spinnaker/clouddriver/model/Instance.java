/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model;

import java.util.List;
import java.util.Map;

/**
 * Primarily a marker interface, but provides the representation of an instance, which exists within a {@link ServerGroup}. Concrete implementations should provide more-specific data.
 *
 *
 */
public interface Instance {
  /**
   * The name of the instance.  By convention this is expected to be globally unique.
   *
   * @return instance name
   */
  String getName();

  /**
   * The human-readable name of the instance
   *
   * @return human-readable name
   */
  default String getHumanReadableName() {
    return getName();
  }

  /**
   * A status of the health of the instance
   * @return HealthState
   */
  HealthState getHealthState();

  /**
   * A timestamp indicating when the instance was launched
   *
   * @return the number of milliseconds after the beginning of time (1 January, 1970 UTC) when
   * this instance was launched
   */
  Long getLaunchTime();

  /**
   * A zone specifier indicating where the instance resides
   *
   * @return the availability zone
   */
  String getZone();

  /**
   * A list of all health metrics reported for this instance
   *
   * @return A list of health metrics, which will always include keys for type and status,
   * and may include others, depending on the health metric
   */
  List<Map<String, Object>> getHealth();

  /**
   * @deprecated use #getCloudProvider
   */
  String getProviderType();

  /**
   * Cloud-provider key, e.g. "aws", "titus"
   * @return
   */
  String getCloudProvider();
}
