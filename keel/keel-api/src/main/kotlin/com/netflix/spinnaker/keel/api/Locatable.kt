package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.LocationConstants.DEFAULT_SUBNET_PURPOSE
import com.netflix.spinnaker.keel.api.LocationConstants.DEFAULT_VPC_NAME
import com.netflix.spinnaker.keel.api.LocationConstants.defaultVPC

/**
 * A resource spec which is located in an account and one or more regions.
 */
interface Locatable<T : Locations<*>> : ResourceSpec {
  val locations: T
}

/** A [Locations] type for resources where the regions alone are sufficient information */
data class SimpleRegions(override val regions: Set<SimpleRegionSpec>) : Locations<SimpleRegionSpec>

interface AccountAwareLocations<T : RegionSpec> : Locations<T> {
  val account: String
}

/** A [Locations] type for resources where the VPC and subnet matter. */
data class SubnetAwareLocations(
  override val account: String,
  /**
   * If not specified here, this should be derived from a default subnet purpose using [vpc].
   */
  val subnet: String?,
  // TODO: this is not ideal as we'd like this default to be configurable
  val vpc: String? = defaultVPC(subnet),
  override val regions: Set<SubnetAwareRegionSpec>
) : AccountAwareLocations<SubnetAwareRegionSpec> {
  // TODO: probably should be an extension at use-site
  fun withDefaultsOmitted() =
    copy(
      vpc = if (vpc == DEFAULT_VPC_NAME) {
        null
      } else {
        vpc
      },
      subnet = if (subnet == DEFAULT_SUBNET_PURPOSE.format(vpc)) {
        null
      } else {
        subnet
      }
    )
}

/** A [Locations] type for resources where the VPC matters. */
data class SimpleLocations(
  override val account: String,
  // TODO: this is not ideal as we'd like this default to be configurable
  val vpc: String? = DEFAULT_VPC_NAME,
  override val regions: Set<SimpleRegionSpec>
) : AccountAwareLocations<SimpleRegionSpec>

object LocationConstants {
  const val DEFAULT_VPC_NAME = "vpc0"
  const val DEFAULT_SUBNET_PURPOSE = "internal (%s)"

  fun defaultVPC(subnet: String?) =
    subnet?.let { Regex("""^.+\((.+)\)$""").find(it)?.groupValues?.get(1) } ?: "vpc0"
}

fun SubnetAwareLocations.toSimpleLocations() =
  SimpleLocations(
    account,
    vpc,
    regions
      .map { SimpleRegionSpec(it.name) }
      .toSet()
  )
