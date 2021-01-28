package com.netflix.spinnaker.keel.notifications

/**
 * All valid notifiers
 */
enum class NotificationType {
  RESOURCE_UNHEALTHY,
  ARTIFACT_PINNED,
  ARTIFACT_UNPINNED,
  ARTIFACT_MARK_AS_BAD,
  APPLICATION_PAUSED,
  APPLICATION_RESUMED,
  LIFECYCLE_EVENT
}
