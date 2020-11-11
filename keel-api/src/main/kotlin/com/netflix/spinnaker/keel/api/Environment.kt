package com.netflix.spinnaker.keel.api

data class Environment(
  val name: String,
  val resources: Set<Resource<*>> = emptySet(),
  val constraints: Set<Constraint> = emptySet(),
  val verifyWith: Set<Verification> = emptySet(),
  val notifications: Set<NotificationConfig> = emptySet() // applies to each resource
) {
  override fun toString(): String = "Environment $name"
}

val Set<Constraint>.anyStateful: Boolean
  get() = any { it is StatefulConstraint }

val Set<Constraint>.statefulCount: Int
  get() = filterIsInstance<StatefulConstraint>().size
