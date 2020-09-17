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
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance

class AmazonInstance implements Instance, Serializable {

  static final long START_TIME_DRIFT = 180000

  String name
  Long launchTime
  List<Map<String, Object>> health = []
  final String providerType = AmazonCloudProvider.ID
  final String cloudProvider = AmazonCloudProvider.ID

  @JsonIgnore
  private Map<String, Object> extraAttributes = new LinkedHashMap<String, Object>()

  @JsonAnyGetter
  Map<String,Object> getExtraAttributes() {
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

  @Override
  HealthState getHealthState() {
    someUpRemainingUnknown(health) ? HealthState.Up :
      anyStarting(health) ? HealthState.Starting :
        anyDown(health) ? HealthState.Down :
          anyOutOfService(health) ? HealthState.OutOfService :
            getLaunchTime() > System.currentTimeMillis() - START_TIME_DRIFT ? HealthState.Starting :
              HealthState.Unknown

  }

  private static boolean anyDown(List<Map<String, String>> healthList) {
    healthList.any { it.state == HealthState.Down.toString()}
  }

  private static boolean someUpRemainingUnknown(List<Map<String, String>> healthList) {
    List<Map<String, String>> knownHealthList = healthList.findAll{ it.state != HealthState.Unknown.toString() }
    knownHealthList ? knownHealthList.every { it.state == HealthState.Up.toString() } : false
  }

  private static boolean anyStarting(List<Map<String, String>> healthList) {
    healthList.any { it.state == HealthState.Starting.toString()}
  }

  private static boolean anyOutOfService(List<Map<String, String>> healthList) {
    healthList.any { it.state == HealthState.OutOfService.toString()}
  }

  @Override
  String getZone() {
    getExtraAttributes().get("placement")?.availabilityZone
  }

  String getAvailabilityZone() {
    return this.getZone()
  }

  @Override
  boolean equals(Object o) {
    if (o instanceof AmazonInstance)
    o.name.equals(name)
  }

  @Override
  int hashCode() {
    return name.hashCode()
  }
}
