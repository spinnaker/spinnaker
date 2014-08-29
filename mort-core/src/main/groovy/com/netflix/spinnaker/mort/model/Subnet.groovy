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

package com.netflix.spinnaker.mort.model

/**
 * A representation of a subnet
 */
interface Subnet {
  /**
   * The ID associated with this subnet
   * @return
   */
  String getId()

  /**
   * The addressable range for this subnet
   * @return
   */
  AddressableRange getAddressableRange()

  /**
   * The purpose for this subnet. Examples: internal, external, secure, performance, etc
   * @return
   */
  String getPurpose()

  /**
   * A set of security groups applicable to this subnet
   * @return
   */
  Set<SecurityGroup> getSecurityGroups()

  /**
   * Applications that exist within this subnet
   *
   * @return
   */
  Set<String> getApplications()

  /**
   * Load balancers associated with this subnet
   *
   * @return
   */
  Set<String> getLoadBalancers()
}
