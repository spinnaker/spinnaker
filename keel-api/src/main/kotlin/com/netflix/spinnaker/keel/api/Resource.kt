package com.netflix.spinnaker.keel.api

/**
 * Internal representation of a resource.
 */
data class Resource<T : ResourceSpec>(
  val apiVersion: String,
  val kind: String,
  val metadata: Map<String, Any?>,
  val spec: T
) {
  init {
    require(kind.isNotEmpty()) { "resource kind must be defined" }
    require(metadata["id"].isValidId()) { "resource id must be a valid id" }
    require(metadata["serviceAccount"].isValidServiceAccount()) { "serviceAccount must be a valid service account" }
    require(metadata["application"].isValidApplication()) { "application must be a valid application" }
  }

  // TODO: this is kinda dirty, but because we add uid to the metadata when persisting we don't really want to consider it in equality checks
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Resource<*>

    if (apiVersion != other.apiVersion) return false
    if (kind != other.kind) return false
    if (spec != other.spec) return false

    return true
  }

  override fun hashCode(): Int {
    var result = apiVersion.hashCode()
    result = 31 * result + kind.hashCode()
    result = 31 * result + spec.hashCode()
    return result
  }
}

val <T : ResourceSpec> Resource<T>.id: String
  get() = metadata.getValue("id").toString()

val <T : ResourceSpec> Resource<T>.serviceAccount: String
  get() = metadata.getValue("serviceAccount").toString()

val <T : ResourceSpec> Resource<T>.application: String
  get() = metadata.getValue("application").toString()

private fun Any?.isValidId() =
  when (this) {
    is String -> isNotBlank()
    else -> false
  }

private fun Any?.isValidServiceAccount() =
  when (this) {
    is String -> isNotBlank()
    else -> false
  }

private fun Any?.isValidApplication() =
  when (this) {
    is String -> isNotBlank()
    else -> false
  }
