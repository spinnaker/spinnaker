/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.documentation.Empty;

import java.util.List;
import java.util.Set;

/**
 * A loadBalancerProvider is an interface for the application to retrieve {@link LoadBalancer} objects. The interface provides a common contract for which one or many providers can be queried for
 * their knowledge of load balancers at a given depth of specificity.
 *
 * This is a temporary class for consolidating the load balancer controllers for each cloud provider.
 * Each cloud provider-specific controller will implement this interface (it already does
 * implicitly, this interface just makes it explicit). Then, this interface will be merged into the
 * LoadBalancerProvider interface while each controller will merge with its
 * &lt;Cloud &gt;LoadBalancerProvider.
 */
public interface LoadBalancerProvider<T extends LoadBalancer> {
  String getCloudProvider();

  List<? extends Item> list();

  Item get(String name);

  List<? extends Details> byAccountAndRegionAndName(String account, String region, String name);

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
  Set<T> getApplicationLoadBalancers(String application);

  // Some providers call this a "Summary", which I think is semantically different from what it is:
  // a details view object, grouped by account, then region.
  interface Item {
    String getName();

    @JsonProperty("accounts")
    List<? extends ByAccount> getByAccounts();
  }

  interface ByAccount {
    String getName();

    @JsonProperty("regions")
    List<? extends ByRegion> getByRegions();
  }

  interface ByRegion {
    @JsonProperty("name")
    String getName();

    @JsonProperty("loadBalancers")
    List<? extends Details> getLoadBalancers();
  }

  interface Details { }
}
