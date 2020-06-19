package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy
import com.netflix.spinnaker.keel.core.DEBIAN_VERSION_COMPARATOR

object DebianSemVerVersioningStrategy : VersioningStrategy() {
  override val type: String = "debian"

  override val comparator: Comparator<String> =
    DEBIAN_VERSION_COMPARATOR

  override fun toString(): String =
    javaClass.simpleName

  override fun equals(other: Any?): Boolean {
    return other is DebianSemVerVersioningStrategy
  }
}
