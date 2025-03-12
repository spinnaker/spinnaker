/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.pipeline

/**
 * Builds a server group
 */
fun serverGroup(block: () -> Map<String, Any>): Map<String, Any> {
  val sg = mutableMapOf(
    "account" to "prod",
    "availabilityZones" to mapOf(
      "us-west-1" to listOf("us-west-1a", "us-west-1c")
    ),
    "capacity" to mapOf("desired" to 1, "max" to 1, "min" to 1),
    "cloudProvider" to "aws",
    "cooldown" to 10,
    "copySourceCustomBlockDeviceMappings" to true,
    "ebsOptimized" to false,
    "enabledMetrics" to listOf<Any>(),
    "healthCheckGracePeriod" to 600,
    "healthCheckType" to "EC2",
    "iamRole" to "spindemoInstanceProfile",
    "instanceMonitoring" to true,
    "instanceType" to "m3.large",
    "interestingHealthProviderNames" to listOf("Amazon"),
    "keyPair" to "nf-prod-keypair-a",
    "loadBalancers" to listOf<Any>(),
    "provider" to "aws",
    "securityGroups" to listOf("sg-b575ded0", "sg-b775ded2", "sg-dbe43abf"),
    "spotPrice" to "",
    "subnetType" to "internal (vpc0)",
    "suspendedProcesses" to listOf<Any>(),
    "tags" to mapOf<String, Any>(),
    "targetGroups" to listOf<Any>(),
    "targetHealthyDeployPercentage" to 100,
    "terminationPolicies" to listOf("Default"),
    "useAmiBlockDeviceMappings" to false,
    "useSourceCapacity" to false
  )
  sg.putAll(block())

  return sg
}
