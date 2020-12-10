package com.netflix.spinnaker.jooq

import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.verification.VerificationStatus
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import com.netflix.spinnaker.keel.notifications.NotificationScope
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.pause.PauseScope
import org.jooq.impl.EnumConverter

class ArtifactStatusConverter : EnumConverter<String, ArtifactStatus>(
  String::class.java,
  ArtifactStatus::class.java
)

class ConstraintStatusConverter : EnumConverter<String, ConstraintStatus>(
  String::class.java,
  ConstraintStatus::class.java
)

class PromotionStatusConverter : EnumConverter<String, PromotionStatus>(
  String::class.java,
  PromotionStatus::class.java
)

class EventScopeConverter : EnumConverter<String, PersistentEvent.EventScope>(
  String::class.java,
  PersistentEvent.EventScope::class.java
)

class PauseScopeConverter : EnumConverter<String, PauseScope>(
  String::class.java,
  PauseScope::class.java
)

class LifecycleEventTypeConverter : EnumConverter<String, LifecycleEventType>(
  String::class.java,
  LifecycleEventType::class.java
)

class LifecycleEventStatusConverter : EnumConverter<String, LifecycleEventStatus>(
  String::class.java,
  LifecycleEventStatus::class.java
)

class NotificationScopeConverter : EnumConverter<String, NotificationScope>(
  String::class.java,
  NotificationScope::class.java
)

class NotificationTypeConverter : EnumConverter<String, NotificationType>(
  String::class.java,
  NotificationType::class.java
)

class VerificationStatusConverter : EnumConverter<String, VerificationStatus>(
  String::class.java,
  VerificationStatus::class.java
)
