package com.netflix.spinnaker.keel.titus.optics

import arrow.optics.Lens
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.optics.monikerStackLens
import com.netflix.spinnaker.keel.optics.simpleLocationsAccountLens

/**
 * Lens for getting/setting [TitusClusterSpec.moniker].
 */
val titusClusterSpecMonikerLens: Lens<TitusClusterSpec, Moniker> = Lens(
  get = TitusClusterSpec::moniker,
  set = { spec, moniker -> spec.copy(moniker = moniker) }
)

/**
 * Composed lens for getting/setting the [Moniker.stack] of a [TitusClusterSpec].
 */
val titusClusterSpecStackLens = titusClusterSpecMonikerLens + monikerStackLens

/**
 * Lens for getting/setting [TitusClusterSpec.locations].
 */
val titusClusterSpecLocationsLens: Lens<TitusClusterSpec, SimpleLocations> = Lens(
  get = TitusClusterSpec::locations,
  set = { spec, locations -> spec.copy(locations = locations) }
)

/**
 * Lens for getting/setting [TitusClusterSpec.defaults].
 */
val titusClusterSpecDefaultsLens: Lens<TitusClusterSpec, TitusServerGroupSpec> = Lens(
  get = TitusClusterSpec::defaults,
  set = { spec, defaults -> spec.copy(_defaults = defaults) }
)

/**
 * Composed lens for getting/setting the [SimpleLocations.account] of a [TitusClusterSpec].
 */
val titusClusterSpecAccountLens =
  titusClusterSpecLocationsLens + simpleLocationsAccountLens

/**
 * Lens for getting/setting [TitusServerGroupSpec.containerAttributes].
 */
val titusServerGroupSpecContainerAttributesLens: Lens<TitusServerGroupSpec, Map<String, String>> = Lens(
  get = { it.containerAttributes ?: emptyMap() },
  set = { titusServerGroupSpec, containerAttributes -> titusServerGroupSpec.copy(containerAttributes = containerAttributes) }
)

/**
 * Composed lens for getting/setting the [TitusServerGroupSpec.containerAttributes] of a [TitusClusterSpec.moniker].
 */
val titusClusterSpecContainerAttributesLens =
  titusClusterSpecDefaultsLens + titusServerGroupSpecContainerAttributesLens
