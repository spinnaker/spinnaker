package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy

/**
 * A [VersioningStrategy] for NPM packages.
 */
object NpmVersioningStrategy : VersioningStrategy {
  override val type: String = "npm"

  override val comparator: Comparator<String> =
    NPM_VERSION_COMPARATOR

  override fun toString(): String =
    javaClass.simpleName

  override fun equals(other: Any?): Boolean {
    return other is NpmVersioningStrategy
  }
}
