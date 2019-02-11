package com.netflix.spinnaker.keel.api.ec2

/**
 * Frigga style name.
 */
data class ClusterName(
  val application: String,
  val stack: String? = null,
  val detail: String? = null
) {
  override fun toString() =
    when {
      stack == null && detail != null -> listOf(application, "", detail)
      else -> listOfNotNull(application, stack, detail)
    }
      .joinToString("-")
}

