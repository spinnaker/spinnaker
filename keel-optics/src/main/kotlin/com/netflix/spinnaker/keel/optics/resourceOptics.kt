package com.netflix.spinnaker.keel.optics

import arrow.optics.Lens
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations

/**
 * Lens for getting/setting [Resource.spec].
 */
fun <SPEC : ResourceSpec> resourceSpecLens(): Lens<Resource<SPEC>, SPEC> = Lens(
  get = Resource<SPEC>::spec,
  set = { resource, spec -> resource.copy(spec = spec) }
)

/**
 * Lens for getting/setting [Moniker.stack].
 */
val monikerStackLens: Lens<Moniker, String?> = Lens(
  get = Moniker::stack,
  set = { moniker, stack -> moniker.copy(stack = stack) }
)

/**
 * Lens for getting/setting [SimpleLocations.account].
 */
val simpleLocationsAccountLens: Lens<SimpleLocations, String> = Lens(
  get = SimpleLocations::account,
  set = { locations, account -> locations.copy(account = account) }
)

/**
 * Lens for getting/setting [SubnetAwareLocations.account].
 */
val subnetAwareLocationsAccountLens: Lens<SubnetAwareLocations, String> = Lens(
  get = SubnetAwareLocations::account,
  set = { locations, account -> locations.copy(account = account) }
)
