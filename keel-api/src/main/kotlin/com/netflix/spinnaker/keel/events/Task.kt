package com.netflix.spinnaker.keel.events

/**
 * A task launched to resolve a diff in a resource.
 */
data class Task(
  val id: String,
  val name: String
)
