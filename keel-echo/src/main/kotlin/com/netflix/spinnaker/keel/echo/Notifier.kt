package com.netflix.spinnaker.keel.echo

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.KeelNotificationConfig
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.echo.model.EchoNotification
import com.netflix.spinnaker.keel.echo.model.EchoNotification.InteractiveActions
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.events.NotificationEvent
import com.netflix.spinnaker.keel.notifications.NotificationScope.RESOURCE
import com.netflix.spinnaker.keel.notifications.NotificationType.UNHEALTHY_RESOURCE
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.NotificationRepository
import com.netflix.spinnaker.keel.persistence.OrphanedResourceException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * A class that forwards notifications for resources based on the environment
 * notification config.
 *
 * This class receives every notification, but notifies only once per
 *  notify.waiting-duration (default once per day) if the notification
 *  is a duplicate. It is essentially a rate limiter for notifications.
 *
 * This simple implementation was chosen to allow experimentation with this feature.
 *
 * Note: this implementation is simple functionality. We could add more advanced
 *  functionality by allowing the message to contain whether we should notify
 *  more than once (and the frequency) and saving that into the database.
 *  We could save the notification and check the database periodically to see
 *  what needs to be sent, instead of relying on the events to keep coming.
 *  We could allow the event to contain the full echo notification instead of
 *  building it in this class, which would allow buttons and things in some
 *  messages.
 */
@Configuration
@EnableConfigurationProperties(KeelNotificationConfig::class)
@Component
class Notifier(
  private val echoService: EchoService,
  private val keelRepository: KeelRepository,
  private val notificationRepository: NotificationRepository,
  private val keelNotificationConfig: KeelNotificationConfig,
  private val springEnv: Environment,
  private val spectator: Registry
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  private val NOTIFICATION_SENT_ID = "keel.notification.sent"

  private val notificationsEnabled: Boolean
    get() = springEnv.getProperty("keel.notifications.resource", Boolean::class.java, true)

  @EventListener(NotificationEvent::class)
  fun onResourceNotificationEvent(event: NotificationEvent) {
    if (notificationsEnabled){
      val shouldNotify = notificationRepository.addNotification(event.scope, event.ref, event.type)
      if (shouldNotify) {
        notify(event)
        notificationRepository.markSent(event.scope, event.ref, event.type)
        spectator.counter(
          NOTIFICATION_SENT_ID,
          listOf(BasicTag("type", event.type.name))
        )
          .runCatching { increment() }
          .onFailure {
            log.error("Exception incrementing {} counter: {}", NOTIFICATION_SENT_ID, it)
          }
      }
    }
  }

  /**
   * This method assumes we're notifying about a resource.
   * If / when we notify about something else, we will need to update this assumption
   */
  private fun notify(event: NotificationEvent) {
    log.debug("Sending notifications for ${event.scope.name.toLowerCase()} ${event.ref} with content ${event.message}")
    try {
      val application = keelRepository.getResource(event.ref).application
      val env = keelRepository.environmentFor(event.ref)
      env.notifications.forEach { notificationConfig ->
        val notification = notificationConfig.toEchoNotification(application, event)
        log.debug("Sending notification for resource ${event.ref} with config $notification")
        runBlocking {
          echoService.sendNotification(notification)
        }
      }
    } catch (e: Exception) {
      when (e) {
        is NoSuchResourceException -> {
          log.error("Trying to notify resource ${event.ref} but it doesn't exist. Not notifying.")
          return
        }
        is OrphanedResourceException -> {
          log.error("Trying to notify resource ${event.ref} but it doesn't have an environment. Not notifying.")
          return
        }
        else -> throw e
      }
    }

  }

  private fun NotificationConfig.toEchoNotification(application: String, event: NotificationEvent): EchoNotification {
    return EchoNotification(
      notificationType = EchoNotification.Type.valueOf(type.name.toUpperCase()),
      to = listOf(address),
      severity = EchoNotification.Severity.NORMAL,
      source = EchoNotification.Source(
        application = application
      ),
      additionalContext = mapOf(
        "formatter" to "MARKDOWN",
        "subject" to event.message.subject,
        "body" to event.message.body
      ),
      interactiveActions = generateInteractiveConfig(event.ref, event.type.name, event.message.color)
    )
  }

  private fun generateInteractiveConfig(resourceId: String, notifier: String, color: String): InteractiveActions? =
    if (keelNotificationConfig.enabled) {
      InteractiveActions(
        callbackServiceId = "keel",
        callbackMessageId = "$resourceId:$notifier",
        actions = listOf(),
        color = color
      )
    } else {
      null
    }

  /**
   * Clears a resource notification for the unhealthy notifier,
   * if it exists
   */
  @EventListener(ResourceHealthEvent::class)
  fun onResourceHealthEvent(event: ResourceHealthEvent) {
    if (event.healthy) {
      notificationRepository.clearNotification(RESOURCE, event.resource.id, UNHEALTHY_RESOURCE)
    }
  }
}
