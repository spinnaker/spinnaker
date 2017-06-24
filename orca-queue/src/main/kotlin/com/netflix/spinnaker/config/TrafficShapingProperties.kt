/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.*

@ConfigurationProperties("queue.trafficShaping")
open class TrafficShapingProperties {

  @ConfigurationProperties("queue.trafficShaping.globalRateLimiting")
  open class GlobalRateLimitingProperties : InterceptorProperties()

  @ConfigurationProperties("queue.trafficShaping.applicationRateLimiting")
  open class ApplicationRateLimitingProperties : OverridableCapacityProperties()

  @ConfigurationProperties("queue.trafficShaping.priorityCapacity")
  open class PriorityCapacityProperties : InterceptorProperties() {
    override var priority = 100
    override var capacity = 200
  }
}

open class InterceptorProperties {

  open var learning: Boolean = true
  open var priority: Int = 500
  open var capacity: Int = 100
}

open class OverridableCapacityProperties : InterceptorProperties() {

  override var capacity: Int = 20
  var capacityOverrides: List<Override> = listOf()
  var enforcing: List<String> = listOf()
  var ignoring: List<String> = listOf()

  fun getCapacity(subject: String): Int
    = Optional.ofNullable(capacityOverrides.find { (name) ->  name == subject })
    .map { it.value }.orElse(capacity)

  fun getEnforcing(subject: String): Boolean {
    if (enforcing.contains(subject)) {
      return true
    }
    if (ignoring.contains(subject)) {
      return false
    }
    return !learning
  }

  data class Override(
    val subject: String,
    val value: Int
  )
}
