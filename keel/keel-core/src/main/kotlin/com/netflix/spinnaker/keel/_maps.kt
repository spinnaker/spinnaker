package com.netflix.spinnaker.keel

/**
 * Filters a map to retain only those entries that have non-null values. Also narrows the
 * value type on the map.
 */
@Suppress("UNCHECKED_CAST")
fun <K, V : Any> Map<out K, V?>.filterNotNullValues(): Map<K, V> =
  filterValues { it != null } as Map<K, V>
