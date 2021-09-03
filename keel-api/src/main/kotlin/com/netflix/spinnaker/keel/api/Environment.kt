package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction

data class Environment(
  val name: String,
  val resources: Set<Resource<*>> = emptySet(),
  val constraints: Set<Constraint> = emptySet(),
  val verifyWith: List<Verification> = emptyList(),
  val notifications: Set<NotificationConfig> = emptySet(), // applies to each resource
  val postDeploy: List<PostDeployAction> = emptyList(),
  val isPreview: Boolean = false
) {
  val resourceIds: Set<String>
    get() = resources.mapTo(mutableSetOf(), Resource<*>::id)

  // We declare the metadata field here such that it's not used in equals() and hashCode(), since we don't
  // care about the metadata when comparing environments.
  val metadata: MutableMap<String, Any?> = mutableMapOf()

  val uid: String?
    get() = metadata["uid"] as? String

  val application: String?
    get() = metadata["application"] as? String

  val deliveryConfigName: String?
    get() = metadata["deliveryConfigName"] as? String

  val repoKey: String?
    get() = metadata["repoKey"] as? String

  val branch: String?
    get() = metadata["branch"] as? String

  val pullRequestId: String?
    get() = metadata["pullRequestId"] as? String

  val basedOn: String?
    get() = metadata["basedOn"] as? String

  fun addMetadata(metadata: Map<String, Any?>) =
    apply {
      this@Environment.metadata.putAll(metadata)
    }

  fun addMetadata(vararg metadata: Pair<String, Any?>) =
    apply {
      this@Environment.metadata.putAll(metadata)
    }

  private val repoParts: List<String>? by lazy { repoKey?.split("/") }

  val repoType: String?
    get() = repoParts?.get(0)

  val projectKey: String?
    get() = repoParts?.get(1)

  val repoSlug: String?
    get() = repoParts?.get(2)

  val hasValidPullRequestId: Boolean
    get() = pullRequestId != null && pullRequestId != "-1"

  override fun toString() = "${if (isPreview) "Preview " else ""}Environment $application/$name"
}

val Set<Constraint>.anyStateful: Boolean
  get() = any { it is StatefulConstraint }

val Set<Constraint>.statefulCount: Int
  get() = filterIsInstance<StatefulConstraint>().size
