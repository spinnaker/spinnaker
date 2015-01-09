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

package com.netflix.spinnaker.oort.aws.model

import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance
import spock.lang.Specification

/**
 * Created by zthrash on 1/7/15.
 */
class AmazonInstanceTest extends Specification {

  Instance instance

  def setup() {
    instance = new AmazonInstance()
  }

  def "test getHealthState for up instance"() {
    given:
      instance.isHealthy = true
    when:
      HealthState healthState = instance.getHealthState()
    then:
      healthState == HealthState.Up
  }

  void "test getHealthState for unhealthy with health status"() {
    given:
      instance.isHealthy = false
      instance.health = [[type: "Amazon", state: "Unknown"], [type: "Discovery", state: "Up"], [type: "LoadBalancer", state: "Up"]]
    when:
      HealthState heathState = instance.getHealthState()
    then:
      heathState == HealthState.Down
  }

  void "test getHealthState for unhealthy with no health status"() {
    given:
      instance.isHealthy = false
      instance.health = [[type: "Amazon", state: "Unknown"]]

    when:
      HealthState heathState = instance.getHealthState()

    then:
      heathState == HealthState.Unknown
  }
}
