package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact

/**
 * Netflix-specific conventions for artifact versions.
 */
object NetflixVersions {

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
   * Extracts the full version string matching the accepted pattern, or null if the input doesn't match.
   */
  fun extractVersion(input: String): String? =
    NETFLIX_VERSION_REGEX.find(input)?.groups?.get(0)?.value

  /**
   * Extracts a version display name from the longer version string, leaving out build and git details if present.
   */
  fun getVersionDisplayName(artifact: PublishedArtifact): String {
    val match = NETFLIX_VERSION_REGEX.find(artifact.version)
    val mainVersion = match?.groups?.get(1)?.value
    val preRelease = match?.groups?.get(2)?.value
    return if (mainVersion != null) {
      "$mainVersion${preRelease ?: ""}"
    } else {
      artifact.version
    }
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
}