package com.netflix.spinnaker.keel.api.artifacts

import com.netflix.spinnaker.keel.api.schema.Discriminator
import java.time.Instant

typealias ArtifactType = String

const val DEBIAN: ArtifactType = "deb"
const val DOCKER: ArtifactType = "docker"
const val NPM: ArtifactType = "npm"

/**
 * The release status of an artifact. This may not necessarily be applicable to all
 * [DeliveryArtifact] sub-classes.
 */
enum class ArtifactStatus {
  FINAL, CANDIDATE, SNAPSHOT, RELEASE, UNKNOWN
}

/**
 * Filters for source code branches. The fields within this class are mutually-exclusive.
 *
 * @param name A specific branch to match against.
 * @param startsWith Match branches starting with this string.
 * @param regex A regular expression to match against (e.g. "feature-.*").
 */
data class BranchFilterSpec(
  val name: String? = null,
  val startsWith: String? = null,
  val regex: String? = null
) {
  init {
    require(
      (name != null && startsWith == null && regex == null)
        || (name == null && startsWith != null && regex == null)
        || (name == null && startsWith == null && regex != null)
    ) {
      "Please specify only one of 'name', 'startsWith' or 'regex'."
    }
  }
}

/**
 * Filters for the origin of an artifact in source control.
 *
 * @param branch A [BranchFilterSpec] with branch filters.
 * @param pullRequestOnly Whether to include only artifacts built from pull requests.
 */
data class ArtifactOriginFilterSpec(
  val branch: BranchFilterSpec? = null,
  val pullRequestOnly: Boolean? = false
)

/**
 * An artifact as defined in a [DeliveryConfig].
 *
 * Unlike other places within Spinnaker, this class does not describe a specific instance of a software artifact
 * (i.e. the output of a build that is published to an artifact repository), but rather the high-level properties
 * that allow keel and [ArtifactSupplier] plugins to find/process the actual artifacts.
 */
// TODO: rename to `ArtifactSpec`
abstract class DeliveryArtifact {
  abstract val name: String
  @Discriminator abstract val type: ArtifactType
  abstract val sortingStrategy: SortingStrategy
  /** A friendly reference to use within a delivery config. */
  abstract val reference: String
  /** The delivery config this artifact is a part of. */
  abstract val deliveryConfigName: String?
  /** A set of release statuses to filter by. Mutually exclusive with [from] filters. */
  open val statuses: Set<ArtifactStatus> = emptySet()
  /** Filters for the artifact origin in source control. */
  open val from: ArtifactOriginFilterSpec? = null

  val filteredByBranch: Boolean
    get() = from?.branch != null

  val filteredByPullRequest: Boolean
    get() = from?.pullRequestOnly == true

  val filteredByReleaseStatus: Boolean
    get() = statuses.isNotEmpty()

  fun toArtifactVersion(version: String, status: ArtifactStatus? = null, createdAt: Instant? = null) =
    PublishedArtifact(
      name = name,
      type = type,
      reference = reference,
      version = version,
      metadata = mapOf(
        "releaseStatus" to status,
        "createdAt" to createdAt
      )
    ).normalized()

  fun toLifecycleRef() = "$deliveryConfigName:$reference"

  override fun toString() = "${type.toUpperCase()} artifact $name (ref: $reference)"
}
