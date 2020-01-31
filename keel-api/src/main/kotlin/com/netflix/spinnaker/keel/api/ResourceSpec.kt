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
   * latter remember to annotate the overridden property with
   * [com.fasterxml.jackson.annotation.JsonIgnore].
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
}
