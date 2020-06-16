package com.netflix.spinnaker.keel.api

/**
 * Internal representation of a resource.
 */
data class Resource<out T : ResourceSpec>(
  val kind: ResourceKind,
  val metadata: Map<String, Any?>,
  val spec: T
) {
  init {
    require(metadata["id"].isValidId()) { "resource id must be a valid id" }
    require(metadata["application"].isValidApplication()) { "application must be a valid application" }
  }

  /**
   * Attempts to find an artifact in the delivery config based on information in this resource's spec.
   */
  fun findAssociatedArtifact(deliveryConfig: DeliveryConfig) =
    if (spec is ComputeResourceSpec) {
      // prefer reference-based artifact info
      spec.completeArtifactReferenceOrNull()
        ?.let { ref ->
          deliveryConfig.matchingArtifactByReference(ref.artifactReference)
        }
      // if not found, then try old-style image provider info
        ?: spec.completeArtifactOrNull()
          ?.let { art ->
            deliveryConfig.matchingArtifactByName(art.artifactName, art.artifactType)
          }
    } else {
      null
    }

  // TODO: this is kinda dirty, but because we add uid to the metadata when persisting we don't really want to consider it in equality checks
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Resource<*>

    if (kind != other.kind) return false
    if (spec != other.spec) return false

    return true
  }

  override fun hashCode(): Int {
    var result = kind.hashCode()
    result = 31 * result + spec.hashCode()
    return result
  }
}

/**
 * Creates a resource id in the correct format.
 */
fun generateId(kind: ResourceKind, spec: ResourceSpec) =
  "${kind.group}:${kind.kind}:${spec.id}"

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
