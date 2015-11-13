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
 * A loadBalancerProvider is an interface for the application to retrieve {@link LoadBalancer} objects. The interface provides a common contract for which one or many providers can be queried for
 * their knowledge of load balancers at a given depth of specificity.
 *
 *
 */
interface LoadBalancerProvider<T extends LoadBalancer> {

  /**
   * Retrieves all load balancers, keyed on load balancer name
   *
   * @return loadBalancerName => set of load balancer objects
   */
  Map<String, Set<T>> getLoadBalancers()

  /**
   * Retrieves all load balancers for a specific account
   *
   * @param account
   * @return set of load balancer objects
   */
  Set<T> getLoadBalancers(String account)

  /**
   * Gets the load balancers for a specified cluster in a specified account
   *
   * @param account
   * @param cluster
   * @return a set of load balancers
   */
  Set<T> getLoadBalancers(String account, String cluster)

  /**
   * Load balancer objects are identified by the composite key of their type, name, and region of existence. This method will return a set of load balancers of a specific type from a cluster that
   * exists within a specified account
   *
   * @param account
   * @param cluster
   * @param type
   * @return set of load balancers
   */
  Set<T> getLoadBalancers(String account, String cluster, String type)

  /**
   * This method is a more specific version of {@link #getLoadBalancers(java.lang.String, java.lang.String, java.lang.String)}, filtering down to the load balancer name. This method will return a
   * set of load balancers, as they may exist in different regions
   *
   * @param account
   * @param cluster
   * @param type
   * @param loadBalancerName
   * @return set of regions
   */
  Set<T> getLoadBalancer(String account, String cluster, String type, String loadBalancerName)

  /**
   * Similar to {@link #getLoadBalancer(java.lang.String, java.lang.String, java.lang.String, java.lang.String)}, except with the specificity of region
   *
   * @param account
   * @param cluster
   * @param type
   * @param loadBalancerName
   * @param region
   * @return a specific load balancer
   */
  T getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region)

  /**
   * Returns all load balancers related to an application based on one of the following criteria:
   *   - the load balancer name follows the Frigga naming conventions for load balancers (i.e., the load balancer name starts with the application name, followed by a hyphen)
   *   - the load balancer is used by a server group in the application
   * @param application the name of the application
   * @return a collection of load balancers with all attributes populated and a minimal amount of data
   *         for each server group: its name, region, and *only* the instances attached to the load balancers described above.
   *         The instances will have a minimal amount of data, as well: name, zone, and health related to any load balancers
   */
  @Empty
  Set<T> getApplicationLoadBalancers(String application)
}
