package com.netflix.spinnaker.keel.api.actuation

/**
 * A generic representation of an actuation job as a [Map].
 */
typealias Job = Map<String, Any?>

val Job.type: String
  get() = getValue("type") as String
