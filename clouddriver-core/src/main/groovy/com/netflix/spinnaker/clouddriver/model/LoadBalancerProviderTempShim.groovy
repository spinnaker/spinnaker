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

package com.netflix.spinnaker.clouddriver.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This is a temporary class for consolidating the load balancer controllers for each cloud provider.
 * Each cloud provider-specific controller will implement this interface (it already does
 * implicitly, this interface just makes it explicit). Then, this interface will be merged into the
 * LoadBalancerProvider interface while each controller will merge with its
 * <Cloud>LoadBalancerProvider.
 */
interface LoadBalancerProviderTempShim {

  List<Item> list()

  Item get(String name)

  List<Details> byAccountAndRegionAndName(String account, String region, String name)

  // Some providers call this a "Summary", which I think is semantically different from what it is:
  // a details view object, grouped by account, then region.
  interface Item {
    String getName()

    @JsonProperty("accounts")
    List<ByAccount> getByAccounts()
  }

  interface ByAccount {
    String getName()

    @JsonProperty("regions")
    List<ByRegion> getByRegions()
  }

  interface ByRegion {
    @JsonProperty("name")
    String getName()

    @JsonProperty("loadBalancers")
    List<Details> getLoadBalancers()
  }

  interface Details { }
}
