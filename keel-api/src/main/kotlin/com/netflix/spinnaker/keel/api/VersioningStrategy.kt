package com.netflix.spinnaker.keel.api

/**
 * Strategy for how to sort versions of artifacts.
 */
sealed class VersioningStrategy

object DebianSemVerVersioningStrategy : VersioningStrategy() {
  override fun toString(): String =
    javaClass.simpleName

  override fun equals(other: Any?): Boolean {
    return other is DebianSemVerVersioningStrategy
  }
}

data class DockerVersioningStrategy(
  val strategy: TagVersionStrategy,
  val captureGroupRegex: String? = null
) : VersioningStrategy() {
  override fun toString(): String =
    "${javaClass.simpleName}[strategy=$strategy, captureGroupRegex=$captureGroupRegex]}"
}
