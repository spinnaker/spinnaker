package com.netflix.spinnaker.keel.igor.model

/** A source control branch. */
data class Branch(
  val name: String,
  val ref: String,
  val default: Boolean = false
)
