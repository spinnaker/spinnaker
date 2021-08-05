package com.netflix.spinnaker.keel.notifications

import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.events.EventLevel
import com.netflix.spinnaker.keel.events.EventLevel.ERROR
import java.time.Instant

/**
 * A [DismissibleNotification] that indicates a delivery config failed to import.
 */
data class DeliveryConfigImportFailed(
  override val triggeredAt: Instant,
  override val application: String,
  override val branch: String,
  override val environment: String? = null,
  val repoType: String,
  val projectKey: String,
  val repoSlug: String,
  val commitHash: String,
  override val link: String? = null,
  override var uid: UID? = null
) : DismissibleNotification() {
  override val level: EventLevel = ERROR
  override val triggeredBy: String = "Managed Delivery"
  override val message: String
    get() {
      val repo = "repository $repoType/$projectKey/$repoSlug (commit ${commitHash.short})"
        .let { if (link != null) "[$it]($link)" else it }
      return "Delivery config for application $application failed to import from $repo"
    }

  private val String.short: String
    get() = substring(0, 7)
}
