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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.clouddriver.documentation.Empty;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * A server group provides a relationship to many instances, and exists within a defined region and one or more zones.
 */
public interface ServerGroup {
  /**
   * The name of the server group
   *
   * @return name
   */
  String getName();

  /**
   * Some arbitrary identifying type for this server group. May provide vendor-specific identification or data-center awareness to callers.
   *
   * @return type
   */
  String getType();

  /**
   * The region in which the instances of this server group are known to exist.
   *
   * @return server group region
   */
  String getRegion();

  /**
   * Some vendor-specific indicator that the server group is disabled
   *
   * @return true if the server group is disabled; false otherwise
   */
  Boolean isDisabled();

  /**
   * Timestamp indicating when the server group was created
   *
   * @return the number of milliseconds after the beginning of time (1 January, 1970 UTC) when
   * this server group was created
   */
  Long getCreatedTime();

  /**
   * The zones within a region that the instances within this server group occupy.
   *
   * @return zones of a region for which this server group has presence or is capable of having presence, or an empty set if none exist
   */
  @Empty
  Set<String> getZones();

  /**
   * The concrete instances that comprise this server group
   *
   * @return set of instances or an empty set if none exist
   */
  @Empty
  Set<Instance> getInstances();

  /**
   * The names of the load balancers associated with this server group
   *
   * @return the set of load balancer names or an empty set if none exist
   */
  @Empty
  Set<String> getLoadBalancers();

  /**
   * The names of the security groups associated with this server group
   *
   * @return the set of security group names or an empty set if none exist
   */
  @Empty
  Set<String> getSecurityGroups();

  /**
   * A collection of attributes describing the launch configuration of this server group
   *
   * @return a map containing various attributes of the launch configuration
   */
  @Empty
  Map<String, Object> getLaunchConfig();

  /**
   * A collection of attributes describing the tags of this server group
   *
   * @return a map containing various tags
   */
  @Empty
  default Map<String, Object> getTags() {
    return null;
  }

  /**
   * A data structure with the total number of instances, and the number of instances reporting each status
   *
   * @return a data structure
   */
  InstanceCounts getInstanceCounts();

  /**
   * The capacity (in terms of number of instances) required for the server group
   *
   * @return
   */
  Capacity getCapacity();

  /**
   * This represents all images deployed to the server group. For most providers, this will be a singleton.
   */
  @JsonIgnore
  ImagesSummary getImagesSummary();

  /**
   * An ImageSummary is collection of data related to the build and VM image of the server group. This is merely a view
   * of data from other parts of this object.
   * <p>
   * Deprecated in favor of getImagesSummary, which is a more generic getImageSummary.
   */
  @JsonIgnore
  @Deprecated
  ImageSummary getImageSummary();

  static class InstanceCounts {
    public Integer getTotal() {
      return total;
    }

    public void setTotal(Integer total) {
      this.total = total;
    }

    public Integer getUp() {
      return up;
    }

    public void setUp(Integer up) {
      this.up = up;
    }

    public Integer getDown() {
      return down;
    }

    public void setDown(Integer down) {
      this.down = down;
    }

    public Integer getUnknown() {
      return unknown;
    }

    public void setUnknown(Integer unknown) {
      this.unknown = unknown;
    }

    public Integer getOutOfService() {
      return outOfService;
    }

    public void setOutOfService(Integer outOfService) {
      this.outOfService = outOfService;
    }

    public Integer getStarting() {
      return starting;
    }

    public void setStarting(Integer starting) {
      this.starting = starting;
    }

    /**
     * Total number of instances in the server group
     */
    private Integer total = 0;
    /**
     * Total number of "Up" instances (all health indicators report "Up" or "Unknown")
     */
    private Integer up = 0;
    /**
     * Total number of "Down" instances (at least one health indicator reports "Down")
     */
    private Integer down = 0;
    /**
     * Total number of "Unknown" instances (all health indicators report "Unknown", or no health indicators reported)
     */
    private Integer unknown = 0;
    /**
     * Total number of "OutOfService" instances (at least one health indicator reports "OutOfService", none are "Down"
     */
    private Integer outOfService = 0;
    /**
     * Total number of "Starting" instances (where any health indicator reports "Starting" and none are "Down" or "OutOfService")
     */
    private Integer starting = 0;
  }

  public static class Capacity {
    public Integer getMin() {
      return min;
    }

    public void setMin(Integer min) {
      this.min = min;
    }

    public Integer getMax() {
      return max;
    }

    public void setMax(Integer max) {
      this.max = max;
    }

    public Integer getDesired() {
      return desired;
    }

    public void setDesired(Integer desired) {
      this.desired = desired;
    }

    /**
     * Minimum number of instances required in this server group. If provider specific {@code ServerGroup} does not have
     * a notion of min then this should be same as {@code desired}
     */
    private Integer min;
    /**
     * Max number of instances required in this server group. If provider specific {@code ServerGroup} does not have
     * a notion of max then this should be same as {@code desired}
     */
    private Integer max;
    /**
     * Desired number of instances required in this server group
     */
    private Integer desired;
  }

  /**
   * Cloud provider-specific data related to the build and VM image of the server group.
   * Deprecated in favor of Images summary
   */
  @JsonInclude(NON_NULL)
  public static interface ImageSummary extends Summary {
    String getServerGroupName();

    String getImageId();

    String getImageName();

    Map<String, Object> getImage();

    @Empty
    Map<String, Object> getBuildInfo();
  }

  /**
   * Cloud provider-specific data related to the build and VM image of the server group.
   */
  @JsonInclude(NON_NULL)
  public static interface ImagesSummary extends Summary {
    List<? extends ImageSummary> getSummaries();
  }
}
