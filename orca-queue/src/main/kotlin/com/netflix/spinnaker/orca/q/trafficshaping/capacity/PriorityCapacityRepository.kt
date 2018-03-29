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
package com.netflix.spinnaker.orca.q.trafficshaping.capacity

enum class Priority {
  CRITICAL, HIGH, MEDIUM, LOW
}

data class GlobalCapacity(
  val ceiling: Int,
  val criticalUsage: Int,
  val highUsage: Int,
  val mediumUsage: Int,
  val lowUsage: Int,
  val learning: Boolean? = null
) {
  fun getTotalUsage() = criticalUsage + highUsage + mediumUsage + lowUsage
  fun shouldShedLoad() = ceiling <= getTotalUsage()
}

interface PriorityCapacityRepository {
  fun incrementExecutions(executionId: String, priority: Priority)
  fun decrementExecutions(executionId: String, priority: Priority)
  fun getGlobalCapacity(): GlobalCapacity
}
