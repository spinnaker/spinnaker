package com.netflix.spinnaker.keel.api

data class Moniker(
  val app: String,
  val stack: String? = null,
  val detail: String? = null,
  val sequence: Int? = null
) {
  override fun toString(): String =
    when {
      stack == null && detail == null -> app
      detail == null -> "$app-$stack"
      else -> "$app-${stack.orEmpty()}-$detail"
    }
}
