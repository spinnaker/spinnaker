package com.netflix.spinnaker.keel.echo

import com.netflix.spinnaker.config.UnhealthyNotificationConfig
import com.netflix.spinnaker.keel.api.AccountAwareLocations
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.events.ClearNotificationEvent
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.events.NotificationEvent
import com.netflix.spinnaker.keel.notifications.ClusterViewParams
import com.netflix.spinnaker.keel.notifications.Notification
import com.netflix.spinnaker.keel.notifications.NotificationScope.RESOURCE
import com.netflix.spinnaker.keel.notifications.NotificationType.UNHEALTHY_RESOURCE
import com.netflix.spinnaker.keel.notifications.friendlyDuration
import com.netflix.spinnaker.keel.persistence.UnhealthyRepository
import com.netflix.spinnaker.keel.veto.unhealthy.UnsupportedResourceTypeException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Duration

@Configuration
@EnableConfigurationProperties(UnhealthyNotificationConfig::class)
@Component
class UnhealthyNotificationListener(
  private val config: UnhealthyNotificationConfig,
  private val unhealthyRepository: UnhealthyRepository,
  private val publisher: ApplicationEventPublisher,
  private val springEnv: Environment,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String
) {

  private val notificationsEnabled: Boolean
    get() = springEnv.getProperty("keel.notifications.unhealthy", Boolean::class.java, config.enabled)

  @EventListener(ResourceHealthEvent::class)
  fun onResourceHealthEvent(event: ResourceHealthEvent) {
    if (notificationsEnabled) {
      if (event.isHealthy) {
        unhealthyRepository.markHealthy(event.resource.id)
        publisher.publishEvent(ClearNotificationEvent(RESOURCE, event.resource.id, UNHEALTHY_RESOURCE))
      } else {
        unhealthyRepository.markUnhealthy(event.resource.id)
        val unhealthyDuration = unhealthyRepository.durationUnhealthy(event.resource.id)
        if (unhealthyDuration > config.minUnhealthyDuration) {
          publisher.publishEvent(
            NotificationEvent(RESOURCE, event.resource.id, UNHEALTHY_RESOURCE, message(event, unhealthyDuration))
          )
        }
      }
    }
  }

  /**
   *  Assumption: health is only for clusters, and we have specific requirements
   *  about what the spec looks like in order to construct the notification link
   */
  fun message(event: ResourceHealthEvent, unhealthyDuration: Duration): Notification {
    val spec = event.resource.spec
    if (spec !is Monikered) {
      throw UnsupportedResourceTypeException("Resource kind ${event.resource.kind} must be monikered to construct resource links")
    }
    if (spec !is Locatable<*>) {
      throw UnsupportedResourceTypeException("Resource kind ${event.resource.kind} must be locatable to construct resource links")
    }
    val locations = spec.locations
    if (locations !is AccountAwareLocations) {
      throw UnsupportedResourceTypeException("Resource kind ${event.resource.kind} must be have account aware locations to construct resource links")
    }

    val params = ClusterViewParams(
      acct = locations.account,
      q = spec.displayName,
      stack = spec.moniker.stack ?: "(none)",
      detail = spec.moniker.detail ?: "(none)"
    )
    val resourceUrl = "$spinnakerBaseUrl/#/applications/${event.resource.application}/clusters?${params.toURL()}"

    return Notification(
      subject = "${spec.displayName} is unhealthy in ${friendlyRegionList(event.unhealthyRegions, event.totalRegions)}",
      body = "<$resourceUrl|${event.resource.id}> has been unhealthy for ${friendlyDuration(unhealthyDuration)} and " +
        "Spinnaker can't fix it. " +
        "Manual intervention might be required. Please check the History view for more details.",
      color = "#FF4949"
    )
  }

  fun friendlyRegionList(regions: List<String>, totalRegions: Int): String =
    when {
      regions.size == 1 -> regions.first()
      regions.size == 2 && regions.size < totalRegions -> regions.joinToString(" and ")
      regions.size < totalRegions -> {
        val r = regions.toMutableList()
        r[r.size - 1] = "and " + r.last()
        r.joinToString(", ")
      }
      else -> "all regions"
    }
}
