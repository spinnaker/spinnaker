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
package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.VersionedArtifactProvider
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy.OldestInstance
import java.time.Duration

data class ServerGroup(
  /**
   * This field is immutable, so we would never be reacting to a diff on it. If the name differs,
   * it's a different resource. Also, a server group name retrieved from CloudDriver will include
   * the sequence number. However, when we resolve desired state from a [ClusterSpec] this field
   * will _not_ include the sequence number. Having it on the model returned from CloudDriver is
   * useful for some things (e.g. specifying ancestor server group when red-blacking a new version)
   * but is meaningless for a diff.
   */
  @get:ExcludedFromDiff
  val name: String,
  val location: Location,
  val launchConfiguration: LaunchConfiguration,
  val capacity: Capacity = Capacity(1, 1, 1),
  val dependencies: ClusterDependencies = ClusterDependencies(),
  val health: Health = Health(),
  val scaling: Scaling = Scaling(),
  val tags: Map<String, String> = emptyMap(),
  @get:ExcludedFromDiff
  val image: ActiveServerGroupImage? = null,
  @get:ExcludedFromDiff
  val buildInfo: BuildInfo? = null,
  @get:ExcludedFromDiff
  override val artifactName: String? = null,
  @get:ExcludedFromDiff
  override val artifactType: ArtifactType? = DEBIAN,
  @get:ExcludedFromDiff
  override val artifactVersion: String? = null,
  @get:ExcludedFromDiff
  val instanceCounts: InstanceCounts? = null
) : VersionedArtifactProvider {
  init {
    require(
      capacity.desired != null && !scaling.hasScalingPolicies() ||
        capacity.desired == null && scaling.hasScalingPolicies()
    ) {
      "capacity.desired and auto-scaling policies are mutually exclusive"
    }
  }

  data class ActiveServerGroupImage(
    val imageId: String,
    val appVersion: String?,
    val baseImageVersion: String?,
    val name: String,
    val imageLocation: String,
    val description: String?
  )

  data class BuildInfo(
    val packageName: String?
  )

  data class Health(
    val cooldown: Duration = Duration.ofSeconds(10),
    val warmup: Duration = Duration.ofSeconds(600),
    val healthCheckType: HealthCheckType = HealthCheckType.EC2,
    val enabledMetrics: Set<Metric> = emptySet(),
    // Note: the default for this in Deck is currently setOf(TerminationPolicy.Default), but we were advised by Netflix
    // SRE to change the default to OldestInstance
    val terminationPolicies: Set<TerminationPolicy> = setOf(OldestInstance)
  )

  data class InstanceCounts(
    val total: Int,
    val up: Int,
    val down: Int,
    val unknown: Int,
    val outOfService: Int,
    val starting: Int
  ) {
    // active asg is healthy if all instances are up
    fun isHealthy(noHealth: Boolean): Boolean =
      if (noHealth) (unknown + up) == total
      else up == total
  }

  data class LaunchConfiguration(
    val imageId: String,
    val appVersion: String?,
    val baseImageVersion: String?,
    val instanceType: String,
    val ebsOptimized: Boolean = DEFAULT_EBS_OPTIMIZED,
    val iamRole: String,
    val keyPair: String,
    val instanceMonitoring: Boolean = DEFAULT_INSTANCE_MONITORING,
    val ramdiskId: String? = null
  ) {
    companion object {
      const val DEFAULT_EBS_OPTIMIZED = false
      const val DEFAULT_INSTANCE_MONITORING = false
      // TODO (lpollo): make configurable, or resolve via LaunchConfigurationResolver
      fun defaultIamRoleFor(application: String) = "${application}InstanceProfile"
    }
  }
}

fun Iterable<ServerGroup>.byRegion(): Map<String, ServerGroup> =
  associateBy { it.location.region }
