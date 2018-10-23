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

import com.netflix.spinnaker.clouddriver.documentation.Empty;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A representation of a network load balancer, which is indirectly correlated to a {@link Cluster} through its relationship to {@link ServerGroup} objects. This interface provides a contract for
 * retrieving the name of the load balancer and the names of the server groups that it is servicing.
 *
 *
 */
public interface LoadBalancer {
  /**
   * Name of the load balancer
   *
   * @return name
   */
  String getName();

  /**
   * This resource's moniker
   *
   * @return moniker
   */
  default Moniker getMoniker() {
    return NamerRegistry.getDefaultNamer().deriveMoniker(this);
  }

  /**
   * The type of this load balancer. Can indicate some vendor-specific designation, or cloud provider
   * @deprecated use #getCloudProvider
   * @return type
   */
  String getType();

  /**
   * Provider-specific identifier
   */
  String getCloudProvider();

  /**
   * Account under which this load balancer exists.
   * @return
   */
  String getAccount();

  /**
   * The names of the server groups that this load balancer is servicing.
   *
   * @return set of names or an empty set if none exist
   */
  @Empty
  Set<LoadBalancerServerGroup> getServerGroups();

  default Map<String, String> getLabels() {
    return new HashMap<>();
  }
}
