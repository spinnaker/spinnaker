package com.netflix.spinnaker.keel.api.constraints

/**
 * Used to register subtypes of constraints for serialization
 */
data class SupportedConstraintAttributesType<T : ConstraintStateAttributes>(
  val name: String,
  val type: Class<T>
)

inline fun <reified T : ConstraintStateAttributes> SupportedConstraintAttributesType(name: String): SupportedConstraintAttributesType<T> =
  SupportedConstraintAttributesType(name, T::class.java)
