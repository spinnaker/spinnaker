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

package com.netflix.spinnaker.clouddriver.google.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleHealth
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleInstanceHealth
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import groovy.util.logging.Slf4j

@Slf4j
class GoogleInstance2 {

  String name
  String instanceType
  Long launchTime
  String zone
  GoogleInstanceHealth instanceHealth
  List<GoogleLoadBalancerHealth> loadBalancerHealths = []

  private Map<String, Object> dynamicProperties = new HashMap<String, Object>()

  @JsonAnyGetter
  public Map<String, Object> anyProperty() {
    return dynamicProperties;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    dynamicProperties.put(name, value);
  }

  @Override
  boolean equals(Object o) {
    if (o instanceof GoogleInstance2) {
      o.name.equals(name)
    }
  }

  @Override
  int hashCode() {
    return name.hashCode()
  }

  @JsonIgnore
  View getView() {
    new View()
  }

  class View implements Instance {

    final String providerType = "gce"

    String name = GoogleInstance2.this.name
    String instanceId = GoogleInstance2.this.name
    String instanceType = GoogleInstance2.this.instanceType
    Long launchTime = GoogleInstance2.this.launchTime
    String zone = GoogleInstance2.this.zone

    @JsonAnyGetter
    public Map<String, Object> anyProperty() {
      return GoogleInstance2.this.dynamicProperties;
    }

    @Override
    List<Map<String, Object>> getHealth() {
      ObjectMapper mapper = new ObjectMapper()
      def healths = []
      loadBalancerHealths.each { GoogleLoadBalancerHealth h ->
        healths << mapper.convertValue(h.view, new TypeReference<Map<String, Object>>() {})
      }
      healths << mapper.convertValue(instanceHealth, new TypeReference<Map<String, Object>>() {})
      healths
    }

    @Override
    HealthState getHealthState() {
      def allHealths = loadBalancerHealths.collect { it.view } + instanceHealth
      someUpRemainingUnknown(allHealths) ? HealthState.Up :
          anyStarting(allHealths) ? HealthState.Starting :
              anyDown(allHealths) ? HealthState.Down :
                  anyOutOfService(allHealths) ? HealthState.OutOfService :
                      HealthState.Unknown
    }

    private static boolean anyDown(List<GoogleHealth> healthsList) {
      healthsList.any { it.state == HealthState.Down }
    }

    private static boolean someUpRemainingUnknown(List<GoogleHealth> healthsList) {
      List<GoogleHealth> knownHealthList = healthsList.findAll { it.state != HealthState.Unknown }
      knownHealthList ? knownHealthList.every { it.state == HealthState.Up } : false
    }

    private static boolean anyStarting(List<GoogleHealth> healthsList) {
      healthsList.any { it.state == HealthState.Starting }
    }

    private static boolean anyOutOfService(List<GoogleHealth> healthsList) {
      healthsList.any { it.state == HealthState.OutOfService }
    }
  }
}
