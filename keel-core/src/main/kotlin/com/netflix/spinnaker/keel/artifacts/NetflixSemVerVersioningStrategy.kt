package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.core.NETFLIX_SEMVER_COMPARATOR

/**
 * A [VersioningStrategy] that codifies the versioning scheme conventions at Netflix.
 */
object NetflixSemVerVersioningStrategy : VersioningStrategy {
  override val type: String = "netflix-semver"

  override val comparator: Comparator<String> =
    NETFLIX_SEMVER_COMPARATOR

  private val NETFLIX_VERSION_REGEX = Regex(
    // version digits (capturing group 1)
  "(\\d+\\.\\d+\\.\\d+)" +
    // release qualifier (capturing group 3)
    "([-~](dev|snapshot|rc)(\\.\\d+)?)?" +
    // build number (capturing group 6)
    "(-[h]?(\\d+\\b))?" +
    // commit hash (capturing group 8)
    "(\\.(\\w{7,}))?"
  )

  /**
   * Extracts a version display name from the longer version string, leaving out build and git details if present.
   */
  fun getVersionDisplayName(artifact: PublishedArtifact): String {
    return NETFLIX_VERSION_REGEX.find(artifact.version)?.groups?.get(1)?.value
      ?: artifact.version
  }

  /**
   * Extracts the build number from the version string, if available.
   */
  fun getBuildNumber(artifact: PublishedArtifact): Int? {
    return try {
      NETFLIX_VERSION_REGEX.find(artifact.version)?.groups?.get(6)?.value?.toInt()
    } catch (e: NumberFormatException) {
      null
    }
  }

  /**
   * Extracts the commit hash from the version string, if available.
   */
  fun getCommitHash(artifact: PublishedArtifact): String? {
    return NETFLIX_VERSION_REGEX.find(artifact.version)?.groups?.get(8)?.value
  }

  override fun toString(): String =
    javaClass.simpleName

  override fun equals(other: Any?): Boolean {
    return other is NetflixSemVerVersioningStrategy
  }
}
