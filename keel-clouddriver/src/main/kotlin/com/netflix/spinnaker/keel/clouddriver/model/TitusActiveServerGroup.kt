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

import com.netflix.spinnaker.keel.api.Moniker

/**
 * Fields common to classes that model Titus server groups
 */
interface BaseTitusServerGroup : BaseServerGroup {
  val awsAccount: String
  val placement: Placement
  val image: TitusActiveServerGroupImage
  val iamProfile: String
  val entryPoint: String
  val env: Map<String, String>
  val containerAttributes: Map<String, String>
  val migrationPolicy: MigrationPolicy
  val serviceJobProcesses: ServiceJobProcesses
  val constraints: Constraints
  val tags: Map<String, String>
  val resources: Resources
  val capacityGroup: String
}

/**
 * Objects that are returned when querying for all of the titus server groups associated with a cluster.
 *
 * The only difference between this class and [TitusActiveServerGroup] is that this class allows you to set the
 * [disabled] flag.
 */
data class TitusServerGroup(
  override val name: String,
  override val awsAccount: String,
  override val placement: Placement,
  override val region: String,
  override val image: TitusActiveServerGroupImage,
  override val iamProfile: String,
  override val entryPoint: String,
  override val targetGroups: Set<String>,
  override val loadBalancers: Set<String>,
  override val securityGroups: Set<String>,
  override val capacity: Capacity,
  override val cloudProvider: String,
  override val moniker: Moniker,
  override val env: Map<String, String>,
  override val containerAttributes: Map<String, String> = emptyMap(),
  override val migrationPolicy: MigrationPolicy,
  override val serviceJobProcesses: ServiceJobProcesses,
  override val constraints: Constraints,
  override val tags: Map<String, String>,
  override val resources: Resources,
  override val capacityGroup: String,
  override val disabled: Boolean,
  override val instanceCounts: InstanceCounts,
  override val createdTime: Long
) : BaseTitusServerGroup

fun TitusServerGroup.toActive() =
  TitusActiveServerGroup(
    name = name,
    awsAccount = awsAccount,
    placement = placement,
    region = region,
    image = image,
    iamProfile = iamProfile,
    entryPoint = entryPoint,
    targetGroups = targetGroups,
    loadBalancers = loadBalancers,
    securityGroups = securityGroups,
    capacity = capacity,
    cloudProvider = cloudProvider,
    moniker = moniker,
    env = env,
    containerAttributes = containerAttributes,
    migrationPolicy = migrationPolicy,
    serviceJobProcesses = serviceJobProcesses,
    constraints = constraints,
    tags = tags,
    resources = resources,
    capacityGroup = capacityGroup,
    instanceCounts = instanceCounts,
    createdTime = createdTime
  )

/**
 * Object returned when querying for the active titus server groups associated with a cluster.
 *
 * The only difference between this class and [TitusServerGroup] is that this class does not support setting the
 * [disabled] flag, since the returned object always corresponds to an active server group.
 */
data class TitusActiveServerGroup(
  override val name: String,
  override val awsAccount: String,
  override val placement: Placement,
  override val region: String,
  override val image: TitusActiveServerGroupImage,
  override val iamProfile: String,
  override val entryPoint: String,
  override val targetGroups: Set<String>,
  override val loadBalancers: Set<String>,
  override val securityGroups: Set<String>,
  override val capacity: Capacity,
  override val cloudProvider: String,
  override val moniker: Moniker,
  override val env: Map<String, String>,
  override val containerAttributes: Map<String, String> = emptyMap(),
  override val migrationPolicy: MigrationPolicy,
  override val serviceJobProcesses: ServiceJobProcesses,
  override val constraints: Constraints,
  override val tags: Map<String, String>,
  override val resources: Resources,
  override val capacityGroup: String,
  override val instanceCounts: InstanceCounts,
  override val createdTime: Long
) : BaseTitusServerGroup

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
