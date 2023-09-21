package com.netflix.spinnaker.keel.api

/**
 * Implemented by all resource specs.
 */
interface ResourceSpec {

  /**
   * The formal resource name. This is combined with the resource's API version prefix and kind to
   * form the fully-qualified resource id.
   *
   * This can be a property that is part of the spec, or derived from other properties. If the
   * latter, remember to annotate the overridden property with [com.fasterxml.jackson.annotation.JsonIgnore].
   */
  val id: String

  /**
   * The Spinnaker application this resource belongs to.
   *
   * This can be a property that is part of the spec, or derived from other properties. If the
   * latter remember to annotate the overridden property with
   * [com.fasterxml.jackson.annotation.JsonIgnore].
   */
  val application: String

  /**
   * A more descriptive name than the [id], intended for displaying in the UI. This property is
   * not persisted, as it's expected to be calculated by the [ResourceSpec] implementation from
   * other fields.
   */
  val displayName: String

  /**
   * Applies the given [suffix] to the resource [id], and to all aggregate properties of the spec
   * whose names are derived from the [id].
   *
   * @return a copy of the original [ResourceSpec] with the modified identifiers.
   */
  @JvmDefault
  fun deepRename(suffix: String): ResourceSpec =
    throw TODO("Not implemented")
}
