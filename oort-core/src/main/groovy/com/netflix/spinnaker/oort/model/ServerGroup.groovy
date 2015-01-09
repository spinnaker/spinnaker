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

package com.netflix.spinnaker.oort.model

import com.netflix.spinnaker.oort.documentation.Empty

/**
 * A server group provides a relationship to many instances, and exists within a defined region and one or more zones.
 *
 * @author Dan Woods
 */
interface ServerGroup {
  /**
   * The name of the server group
   *
   * @return name
   */
  String getName()

  /**
   * Some arbitrary identifying type for this server group. May provide vendor-specific identification or data-center awareness to callers.
   *
   * @return type
   */
  String getType()

  /**
   * The region in which the instances of this server group are known to exist.
   *
   * @return server group region
   */
  String getRegion()

  /**
   * Some vendor-specific indicator that the server group is disabled
   *
   * @return true if the server group is disabled; false otherwise
   */
  Boolean isDisabled()

  /**
   * Timestamp indicating when the server group was created
   *
   * @return the number of milliseconds after the beginning of time (1 January, 1970 UTC) when
   * this server group was created
   */
  Long getCreatedTime()

  /**
   * The zones within a region that the instances within this server group occupy.
   *
   * @return zones of a region for which this server group has presence or is capable of having presence, or an empty set if none exist
   */
  @Empty
  Set<String> getZones()

  /**
   * The concrete instances that comprise this server group
   *
   * @return set of instances or an empty set if none exist
   */
  @Empty
  Set<Instance> getInstances()

  /**
   * The names of the load balancers associated with this server group
   *
   * @return the set of load balancer names or an empty set if none exist
   */
  @Empty
  Set<String> getLoadBalancers()

  /**
   * The names of the security groups associated with this server group
   *
   * @return the set of security group names or an empty set if none exist
   */
  @Empty
  Set<String> getSecurityGroups()

  /**
   * A collection of attributes describing the launch configuration of this server group
   *
   * @return a map containing various attributes of the launch configuration
   */
  @Empty
  Map<String, Object> getLaunchConfig()

  /**
   * A data structure with instance counts, including total, number of up, and number of down instances
   *
   * @return a data structure
   */
  InstanceCounts getInstanceCounts()

  static class InstanceCounts {
    /**
     * Total number of instances in the server group
     */
    Integer total

    /**
     * Total number of "up" instances (which are healthy)
     */
    Integer up

    /**
     * Total number of "down" instances (which are unhealthy)
     */
    Integer down

    /**
     * Total number of "unknown" instances (which have a questionable health status)
     */
    Integer unknown
  }
}