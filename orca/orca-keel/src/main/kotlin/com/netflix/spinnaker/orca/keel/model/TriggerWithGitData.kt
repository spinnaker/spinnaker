package com.netflix.spinnaker.orca.keel.model

data class TriggerWithGitData(
  val payload: GitPayload,
  val hash: String,
  val source: String, //like "stash
  val project: String,
  val branch: String,
  val slug: String,
  val link: String,
)

data class GitPayload(
  val causedBy: CausedBy,
  val source: Source,
  val pullRequest: PullRequest
)

data class CausedBy(
  val name: String,
  val email: String,
)

data class Source(
  val projectKey: String,
  val repoName: String,
  val branchName: String,
  val message: String,
  val sha: String,
  val url: String
)
