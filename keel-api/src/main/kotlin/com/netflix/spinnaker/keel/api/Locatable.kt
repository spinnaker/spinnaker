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

data class SubnetAwareLocations(
  override val account: String,
  /**
   * If not specified here, this should be derived from a default subnet purpose using [vpc].
   */
  val subnet: String?,
  // TODO: this is not ideal as we'd like this default to be configurable
  override val vpc: String? = defaultVPC(subnet),
  override val regions: Set<SubnetAwareRegionSpec>
) : Locations<SubnetAwareRegionSpec> {
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

data class SimpleLocations(
  override val account: String,
  // TODO: this is not ideal as we'd like this default to be configurable
  override val vpc: String? = DEFAULT_VPC_NAME,
  override val regions: Set<SimpleRegionSpec>
) : Locations<SimpleRegionSpec>

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
