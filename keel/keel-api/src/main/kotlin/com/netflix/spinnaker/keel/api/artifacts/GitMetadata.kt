package com.netflix.spinnaker.keel.api.artifacts

/**
 * The git metadata of an artifact.
 */
data class GitMetadata(
  val commit: String, // commit hash, can be short or long sha
  val author: String? = null,
  val project: String? = null, // the project name, like SPKR
  val branch: String? = null,
  val repo: Repo? = null,
  val pullRequest: PullRequest? = null,
  val commitInfo: Commit? = null
)

data class Repo(
  val name: String? = null,
  val link: String? = null
)

data class PullRequest(
  val number: String? = null,
  val url: String? = null
)

data class Commit(
  val sha: String? = null,
  val link: String? = null,
  val message: String? = null
)
