package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.events.EventLevel.ERROR
import com.netflix.spinnaker.keel.events.EventLevel.INFO
import com.netflix.spinnaker.keel.notifications.DeliveryConfigImportFailed
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import com.netflix.spinnaker.time.MutableClock
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isFalse
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotNull
import strikt.assertions.isSuccess
import strikt.assertions.isTrue

class SqlDismissibleNotificationRepositoryTests {
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))
  private val clock = MutableClock()
  private val objectMapper = configuredTestObjectMapper()
  private val deliveryConfigRepository = SqlDeliveryConfigRepository(jooq, clock, mockk(), objectMapper, sqlRetry, publisher = mockk())
  private val notificationRepository = SqlDismissibleNotificationRepository(jooq, sqlRetry, objectMapper, clock)
  private val deliveryConfig = deliveryConfig()

  // TODO: is there a way to extend the @JsonSubType definitions in a test, so I can create my own subclass here?
  private val notification = DeliveryConfigImportFailed(
    triggeredAt = clock.instant(),
    application = deliveryConfig.application,
    repoType = "stash",
    projectKey = "proj",
    repoSlug = "repo",
    commitHash = "asdf1234",
    link = "https://some.link"
  )

  @BeforeEach
  fun setup() {
    deliveryConfigRepository.store(deliveryConfig)
  }

  @AfterEach
  fun teardown() {
    cleanupDb(jooq)
  }

  @Test
  fun `correctly stores notification`() {
    expectCatching {
      notificationRepository.storeNotification(notification)
    }.isSuccess()

    val storedNotification = notificationRepository.notificationHistory(deliveryConfig.application, false, emptySet(), 1).first()
    expectThat(storedNotification)
      .isEqualTo(notification.copy(uid = storedNotification.uid))
      .get { uid }.isNotNull()
  }

  @Disabled // TODO: need a sub-class of DismissibleNotification that exposes environment
  @Test
  fun `throws an exception for unknown environment name`() {
    expectCatching {
      //notificationRepository.storeNotificationFromEvent(notification.copy(environment = "bananas"))
    }.isFailure()
  }

  @Test
  fun `correctly retrieves notification history`() {
    repeat(5) {
      notificationRepository.storeNotification(
        notification.copy(triggeredAt = clock.tickMinutes(1))
      )
    }
    val history = notificationRepository.notificationHistory(deliveryConfig.application)
    expectThat(history)
      .hasSize(5)
    expectThat(history.first().triggeredAt)
      .isGreaterThan(history.last().triggeredAt)
    expectThat(history)
      .all { get { isActive }.isTrue() }
  }

  @Test
  fun `correctly dismisses notification`() {
    val uid = notificationRepository.storeNotification(notification)
    expectThat(notificationRepository.dismissNotification(uid, "keel@keel.io"))
      .isTrue()

    val updatedNotification = notificationRepository.notificationHistory(deliveryConfig.application, limit = 1).first()
    expectThat(updatedNotification) {
      get { isActive }.isFalse()
      get { dismissedBy }.isEqualTo("keel@keel.io")
      get { dismissedAt }.isNotNull()
    }
  }
  @Test
  fun `can filter history by active status`() {
    repeat(10) {
      val uid = notificationRepository.storeNotification(
        notification.copy(triggeredAt = clock.tickMinutes(1))
      )
      if (it < 5) {
        notificationRepository.dismissNotification(uid, "keel@keel.io")
      }
    }

    expectThat(notificationRepository.notificationHistory(deliveryConfig.application, onlyActive = true))
      .hasSize(5)
  }

  @Test
  fun `can filter history by event level`() {
    repeat(5) {
      notificationRepository.storeNotification(
        notification.copy(triggeredAt = clock.tickMinutes(1))
      )
    }

    expectThat(notificationRepository.notificationHistory(deliveryConfig.application, levels = setOf(ERROR)))
      .hasSize(5)
    expectThat(notificationRepository.notificationHistory(deliveryConfig.application, levels = setOf(INFO)))
      .hasSize(0)
  }
}
