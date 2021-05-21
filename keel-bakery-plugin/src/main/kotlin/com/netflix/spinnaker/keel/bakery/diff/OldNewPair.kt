package com.netflix.spinnaker.keel.bakery.diff

/**
 * A simple pair of values of the same type (named [old] and [new] as opposed to Pair's first and second)..
 */
data class OldNewPair<T : Any> (
  val old: T,
  val new: T
)
