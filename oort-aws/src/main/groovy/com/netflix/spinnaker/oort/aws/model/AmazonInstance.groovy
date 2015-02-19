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

package com.netflix.spinnaker.oort.aws.model

import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance

class AmazonInstance extends HashMap implements Instance, Serializable {

  AmazonInstance(String name) {
    setProperty "name", name
  }

  @Override
  String getName() {
    getProperty "name"
  }

  boolean isHealthy() {
    getProperty "isHealthy"
  }

  @Override
  HealthState getHealthState() {
    List<Map<String, String>> healthList = getHealth()

    someUpRemainingUnknown(healthList) ? HealthState.Up :
      anyStarting(healthList) ? HealthState.Starting :
        anyDown(healthList) ? HealthState.Down :
          anyOutOfService(healthList) ? HealthState.OutOfService :
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
  Long getLaunchTime() {
    ((Map) getProperty("instance")).get("launchTime")
  }

  @Override
  String getZone() {
    getProperty "zone"
  }

  @Override
  List<Map<String, String>> getHealth() {
    getProperty "health"
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
