package com.netflix.spinnaker.keel.lifecycle

import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.FAILED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.SUCCEEDED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.UNKNOWN

/**
 * Possible statuses of a lifecycle step.
 *
 * A [NOT_STARTED] event kicks off monitoring.
 */
enum class LifecycleEventStatus {
  NOT_STARTED, RUNNING, SUCCEEDED, FAILED, UNKNOWN;
}

fun LifecycleEventStatus.isEndingStatus(): Boolean =
  this == SUCCEEDED || this == FAILED || this == UNKNOWN
