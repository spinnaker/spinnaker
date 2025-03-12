package com.netflix.spinnaker.keel.optics

import arrow.optics.Lens

/**
 * Generic lens for getting/setting a keyed value in a map. Setting `null` removes the key from the map.
 */
fun <K, V> mapValueLens(key: K): Lens<Map<K, V>, V?> = Lens(
  get = { it[key] },
  set = { map, value ->
    if (value == null) map - key
    else map + (key to value)
  }
)
