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

    if(anyUpAndNoDown(healthList)) { return HealthState.Up }
    if(anyDown(healthList)) { return HealthState.Down }
    if(allUnknown(healthList)) { return HealthState.Unknown }

  }

  private boolean allUnknown(List<Map<String, String>> healthList) {
    //Only Unknown states use the HealthState enum. Up and Down are strings
    healthList.every { it.state == HealthState.Unknown }
  }

  private boolean anyDown(List<Map<String, String>> healthList) {
    healthList.any { it.state == "Down"}
  }

  private boolean anyUpAndNoDown(List<Map<String, String>> healthList) {
    List knownHealthList = healthList.findAll{ it.state != HealthState.Unknown }
    knownHealthList ? knownHealthList.every { it.state == "Up" } : false
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
