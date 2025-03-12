package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Given a base (a.k.a. "left") and a working (a.k.a. "right") objects of type T to compare, create a "diff" of those
 * objects and use only the properties whose values are different between the two to create an instance of a
 * ResourceSpec-like class of type S with the corresponding values from the working object.
 *
 * If all the diff properties have a null value in the working object, returns null.
 */
inline fun <reified T : Any, reified S : Any> buildSpecFromDiff(
  base: T,
  working: T,
  allowedProperties: Set<String>? = null,
  forcedProperties: Set<String> = emptySet()
): S? {
  val diff = DefaultResourceDiff(working, base)

  if (!diff.hasChanges()) {
    return null
  }

  // for the purpose of building specs, we ignore removed values on the working object as the base will either
  // be a baseline object with defaults or to calculate overrides from, so we let the base take precedence in
  // that case
  val addedOrChangedProps = diff.children
    .filter { it.isAdded || it.isChanged }
    .map { it.propertyName }
    .toSet()

  // build a map of constructor parameters including only those present in the diff that are allowed,
  // unless they're forcefully included
  var filteredParams = addedOrChangedProps
    .also {
      if (allowedProperties != null) {
        it intersect allowedProperties
      }
      it union forcedProperties
    }

  if (filteredParams.isEmpty()) {
    return null
  }

  val ctor = S::class.primaryConstructor!!
  val params = ctor.parameters
    .filter { param -> param.name in filteredParams }
    .map { param ->
      val prop = T::class.memberProperties.find { prop -> prop.name == param.name }
      param to prop?.get(working)
    }
    .toMap()

  return if (params.values.all { it == null }) {
    null
  } else {
    ctor.callBy(params)
  }
}
