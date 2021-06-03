package com.netflix.spinnaker.keel.scm

import com.netflix.spectator.api.BasicTag
import com.netflix.spinnaker.keel.api.scm.CodeEvent
import com.netflix.spinnaker.keel.front50.model.Application

internal val DELIVERY_CONFIG_RETRIEVAL_ERROR = listOf("type" to "deliveryConfig.retrieval", "status" to "error")
internal val DELIVERY_CONFIG_RETRIEVAL_SUCCESS = listOf("type" to "deliveryConfig.retrieval", "status" to "success")

fun CodeEvent.matchesApplicationConfig(app: Application?): Boolean =
  app != null
    && repoType.equals(app.repoType, ignoreCase = true)
    && projectKey.equals(app.repoProjectKey, ignoreCase = true)
    && repoSlug.equals(app.repoSlug, ignoreCase = true)

fun CodeEvent.metricTags(application: String? = null, extraTags: Iterable<Pair<String, String>> = emptySet()): Set<BasicTag>{
  val tags = mutableSetOf(
    BasicTag("event", type),
    BasicTag("repoKey", repoKey),
    BasicTag("targetBranch", targetBranch)
  )
  if (application != null) {
    tags.add(BasicTag("application", application))
  }
  return tags + extraTags.toTags()
}

fun Iterable<Pair<String, String>>.toTags() = map { it.toTags() }

fun Pair<String, String>.toTags() = BasicTag(first, second)
