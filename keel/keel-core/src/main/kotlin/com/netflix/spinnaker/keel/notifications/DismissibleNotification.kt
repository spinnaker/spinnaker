package com.netflix.spinnaker.keel.notifications

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.events.EventLevel
import com.netflix.spinnaker.keel.events.EventLevel.INFO
import java.time.Instant

/**
 * A user-facing notification that can be dismissed.
 */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  include = JsonTypeInfo.As.PROPERTY
)
@JsonSubTypes(
  JsonSubTypes.Type(value = DeliveryConfigImportFailed::class),
)
abstract class DismissibleNotification {
  open val level: EventLevel = INFO
  abstract val triggeredAt: Instant
  abstract val application: String
  abstract val message: String
  open val isActive: Boolean = true
  open val environment: String? = null
  open val branch: String? = null
  open val link: String? = null
  open val triggeredBy: String? = null
  open val dismissedBy: String? = null
  open val dismissedAt: Instant? = null
  open var uid: UID? = null // read from the database
}
