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

package com.netflix.spinnaker.clouddriver.titus.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider

class TitusCluster implements Cluster, Serializable {
  String name
  String type = TitusCloudProvider.ID
  String accountName
  Set<TitusServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<TitusServerGroup>())
  Set<LoadBalancer> loadBalancers = Collections.emptySet()

  @JsonIgnore
  private Map<String, Object> extraAttributes = new LinkedHashMap<String, Object>()

  @JsonAnyGetter
  @Override
  Map<String, Object> getExtraAttributes() {
    return extraAttributes
  }

  /**
   * Setter for non explicitly defined values.
   *
   * Used for both Jackson mapping {@code @JsonAnySetter} as well
   * as Groovy's implicit Map constructor (this is the reason the
   * method is named {@code set(String name, Object value)}
   * @param name The property name
   * @param value The property value
   */
  @JsonAnySetter
  void set(String name, Object value) {
    extraAttributes.put(name, value)
  }
}
