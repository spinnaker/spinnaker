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

package com.netflix.spinnaker.clouddriver.aws.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Cluster
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "accountName"])
class AmazonCluster implements Cluster, Serializable {
  String name
  String type = "aws"
  String accountName
  Set<AmazonServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<AmazonServerGroup>())
  Set<AmazonTargetGroup> targetGroups = Collections.synchronizedSet(new HashSet<AmazonTargetGroup>())
  Set<AmazonLoadBalancer> loadBalancers = Collections.synchronizedSet(new HashSet<AmazonLoadBalancer>())

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
