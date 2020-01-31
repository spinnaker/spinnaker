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
package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.Moniker

data class TitusActiveServerGroup(
  val name: String,
  val awsAccount: String,
  val placement: Placement,
  val region: String,
  val image: TitusActiveServerGroupImage,
  val iamProfile: String,
  val entryPoint: String,
  val targetGroups: Set<String>,
  val loadBalancers: Set<String>,
  val securityGroups: Set<String>,
  val capacity: Capacity,
  val cloudProvider: String,
  val moniker: Moniker,
  val env: Map<String, String>,
  val containerAttributes: Map<String, String> = emptyMap(),
  val migrationPolicy: MigrationPolicy,
  val serviceJobProcesses: ServiceJobProcesses,
  val constraints: Constraints,
  val tags: Map<String, String>,
  val resources: Resources,
  val capacityGroup: String
)

data class Placement(
  val account: String,
  val region: String,
  val zones: List<String> = emptyList()
)

data class MigrationPolicy(
  val type: String = "systemDefault"
)

data class TitusActiveServerGroupImage(
  val dockerImageName: String,
  val dockerImageVersion: String,
  val dockerImageDigest: String
)

data class Resources(
  val cpu: Int = 1,
  val disk: Int = 10000,
  val gpu: Int = 0,
  val memory: Int = 512,
  val networkMbps: Int = 128
)

data class Constraints(
  val hard: Map<String, Any> = emptyMap(),
  val soft: Map<String, Any> = mapOf("ZoneBalance" to "true")
)

data class ServiceJobProcesses(
  val disableIncreaseDesired: Boolean = false,
  val disableDecreaseDesired: Boolean = false
)
