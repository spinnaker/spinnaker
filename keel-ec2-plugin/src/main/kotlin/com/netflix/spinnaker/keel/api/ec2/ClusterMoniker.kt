package com.netflix.spinnaker.keel.api.ec2

data class ClusterMoniker(
  val application: String,
  val stack: String? = null,
  val detail: String? = null
) {
  val cluster: String
    get() = when {
      stack == null && detail == null -> application
      detail == null -> "$application-$stack"
      else -> "$application-${stack.orEmpty()}-$detail"
    }
}
