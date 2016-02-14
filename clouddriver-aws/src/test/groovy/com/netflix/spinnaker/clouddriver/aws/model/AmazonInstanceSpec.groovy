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

package com.netflix.spinnaker.clouddriver.aws.model

import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by zthrash on 1/7/15.
 */
class AmazonInstanceSpec extends Specification {

  Instance instance

  def setup() {
    instance = new AmazonInstance(name: 'foo')
  }

  def "getHealthState for ALL UP health states"() {
    given:
      instance.health = [[type: "Amazon", healthClass: 'platform', state: "Unknown"], [type: "Discovery", state: 'Up'], [type: "LoadBalancer", state: "Up"]]
    when:
      HealthState healthState = instance.getHealthState()
    then:
      healthState == HealthState.Up
  }

  def "getHealthState for ONE DOWN & ONE UP health state"() {
    given:
      instance.health = [[type: "Amazon", healthClass: 'platform', state: "Unknown"], [type: "Discovery", state: "Up"], [type: "LoadBalancer", state: "Down"]]
    when:
      HealthState heathState = instance.getHealthState()
    then:
      heathState == HealthState.Down
  }

  def "getHealthState for ALL DOWN health state"() {
    given:
    instance.health = [[type: "Amazon", healthClass: 'platform', state: "Unknown"], [type: "Discovery", state: "Down"], [type: "LoadBalancer", state: "Down"]]
    when:
    HealthState heathState = instance.getHealthState()
    then:
    heathState == HealthState.Down
  }

  def "getHealthState for unhealthy with no UP or DOWN health status"() {
    given:
      instance.health = [[type: "Amazon", healthClass: 'platform', state: "Unknown"]]

    when:
      HealthState heathState = instance.getHealthState()

    then:
      heathState == HealthState.Unknown
  }

  def "getHealthState for empty health collection"() {
    given:
    instance.health = []

    when:
    HealthState heathState = instance.getHealthState()

    then:
    heathState == HealthState.Unknown
  }

  def "getHealthState for ONE STARTING"() {
    given:
      instance.health = [[type: "Amazon", healthClass: 'platform', state: "Unknown"], [type: "Discovery", state: "Starting"]]
    when:
      HealthState heathState = instance.getHealthState()
    then:
      heathState == HealthState.Starting
  }

  def "getHealthState for ONE STARTING and ONE DOWN"() {
    given:
      instance.health = [[type: "Amazon", healthClass: 'platform', state: "Unknown"], [type: "Discovery", state: "Starting"], [type: "LoadBalancer", state: "Down"]]
    when:
      HealthState heathState = instance.getHealthState()
    then:
      heathState == HealthState.Starting
  }

  def "getHealthState for ONE STARTING and ONE OUTOFSERVICE"() {
    given:
      instance.health = [[type: "Amazon", healthClass: 'platform', state: "Unknown"], [type: "Discovery", state: "Starting"], [type: "LoadBalancer", state: "OutOfService"]]
    when:
      HealthState heathState = instance.getHealthState()
    then:
      heathState == HealthState.Starting
  }

  def "getHealthState for ONE OUTOFSERVICE, and ONE DOWN"() {
    given:
    instance.health = [
      [type: "Amazon", healthClass: 'platform', state: "Unknown"],
      [type: "LoadBalancer", state: "OutOfService"],
      [type: "HowDoWeHaveFourHealthIndicators?", state: "Down"]
    ]
    when:
      HealthState heathState = instance.getHealthState()
    then:
      heathState == HealthState.Down
  }

  @Unroll
  def "getHealthState for no health, drift offset: #drift ms"() {
    given:
      instance.health = []
      instance.launchTime = System.currentTimeMillis() - AmazonInstance.START_TIME_DRIFT - drift

    when:
      HealthState healthState = instance.getHealthState()
    then:
      healthState == expected

    where:
    drift  || expected
    -10000 || HealthState.Starting
    10000  || HealthState.Unknown
  }

}
