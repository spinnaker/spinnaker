/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.api.ec2.cluster

import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import java.time.Duration

data class Health(
  val cooldown: Duration = Duration.ofSeconds(10),
  val warmup: Duration = Duration.ofSeconds(600),
  val healthCheckType: HealthCheckType = HealthCheckType.EC2,
  val enabledMetrics: Set<Metric> = emptySet(),
  val terminationPolicies: Set<TerminationPolicy> = setOf(TerminationPolicy.OldestInstance)
)
