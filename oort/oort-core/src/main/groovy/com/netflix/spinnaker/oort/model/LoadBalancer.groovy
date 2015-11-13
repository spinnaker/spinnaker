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
 * A representation of a network load balancer, which is indirectly correlated to a {@link Cluster} through its relationship to {@link ServerGroup} objects. This interface provides a contract for
 * retrieving the name of the load balancer and the names of the server groups that it is servicing.
 *
 *
 */
interface LoadBalancer {
  /**
   * Name of the load balancer
   *
   * @return name
   */
  String getName()

  /**
   * The type of this load balancer. Can indicate some vendor-specific designation, or cloud provider
   *
   * @return type
   */
  String getType()

  /**
   * The names of the server groups that this load balancer is servicing.
   *
   * @return set of names or an empty set if none exist
   */
  @Empty
  Set<String> getServerGroups()
}
