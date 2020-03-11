package com.netflix.spinnaker.keel.api

data class Environment(
  val name: String,
  val resources: Set<Resource<*>> = emptySet(),
  val constraints: Set<Constraint> = emptySet(),
  val notifications: Set<NotificationConfig> = emptySet() // applies to each resource
)

val Set<Constraint>.anyStateful: Boolean
  get() = any { it is StatefulConstraint }

val Set<Constraint>.statefulCount: Int
  get() = filterIsInstance<StatefulConstraint>().size
