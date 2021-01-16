package com.netflix.spinnaker.keel.notifications

/**
 * All valid notifiers
 */
enum class NotificationType {
  UNHEALTHY_RESOURCE,
  PINNED_ARTIFACT,
  UNPINNED_ARTIFACT
}
