package com.netflix.spinnaker.keel.echo

import com.netflix.spinnaker.keel.api.AccountAwareLocations
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.events.NotificationEvent
import com.netflix.spinnaker.keel.notifications.ClusterViewParams
import com.netflix.spinnaker.keel.notifications.Notification
import com.netflix.spinnaker.keel.notifications.NotificationScope.RESOURCE
import com.netflix.spinnaker.keel.notifications.NotificationType.UNHEALTHY_RESOURCE
import com.netflix.spinnaker.keel.veto.unhealthy.UnsupportedResourceTypeException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component


@Component
class UnhealthyNotificationListener(
  private val publisher: ApplicationEventPublisher,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String,
  private val springEnv: Environment
) {

  private val notificationsEnabled: Boolean
    get() = springEnv.getProperty("keel.notifications.unhealthy", Boolean::class.java, true)

  @EventListener(ResourceHealthEvent::class)
  fun onResourceHealthEvent(event: ResourceHealthEvent) {
    if (notificationsEnabled && !event.healthy){
      publisher.publishEvent(NotificationEvent(RESOURCE, event.resource.id, UNHEALTHY_RESOURCE, message(event.resource)))
    }
  }

  /**
   *  Assumption: health is only for clusters, and we have specific requirements
   *  about what the spec looks like in order to construct the notification link
   *
   *  Future improvement: add in how long the resource has been unhealthy for
   */
  private fun message(resource: Resource<*>): Notification {
    val spec = resource.spec
    if (spec !is Monikered) {
      throw UnsupportedResourceTypeException("Resource kind ${resource.kind} must be monikered to construct resource links")
    }
    if (spec !is Locatable<*>) {
      throw UnsupportedResourceTypeException("Resource kind ${resource.kind} must be locatable to construct resource links")
    }
    val locations = spec.locations
    if (locations !is AccountAwareLocations) {
      throw UnsupportedResourceTypeException("Resource kind ${resource.kind} must be have account aware locations to construct resource links")
    }

    val params = ClusterViewParams(
      acct = locations.account,
      q = resource.spec.displayName,
      stack = spec.moniker.stack ?: "(none)",
      detail = spec.moniker.detail ?: "(none)"
    )
    val resourceUrl = "$spinnakerBaseUrl/#/applications/${resource.application}/clusters?${params.toURL()}"

    return Notification(
      subject = "${resource.spec.displayName} is unhealthy",
      body = "<$resourceUrl|${resource.id}> is unhealthy and " +
        "Spinnaker does not detect any changes to this resource. " +
        "Manual intervention might be required. Please check the History view to see more details.",
      color = "#FF4949"
    )
  }

}