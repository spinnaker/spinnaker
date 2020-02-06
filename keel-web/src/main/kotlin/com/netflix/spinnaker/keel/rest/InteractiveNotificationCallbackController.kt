package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.parseUID
import com.netflix.spinnaker.keel.echo.model.EchoNotification
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/notifications/callback"])
class InteractiveNotificationCallbackController(
  private val deliveryConfigRepository: DeliveryConfigRepository
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostMapping(
    consumes = [MediaType.APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [MediaType.APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  // TODO: This should be validated against write access to a service account. Service accounts should
  //  become a top-level property of either delivery configs or environments.
  //
  // TODO(lfp): Related to the above, we'll need an additional authentication method for interactive constraint
  //  approval outside of the Spinnaker UI, e.g. in Slack, since X-SPINNAKER-USER will be extracted from the Slack
  //  message and not provided by the UI. My plan is to include an OTP in the callback URL. Note that echo
  //  does token/signature verification of messages, so there's relatively tight security there, but we still trust
  //  the e-mail address without a Spinnaker user having authenticated here.
  fun handleInteractionCallback(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @RequestBody callback: EchoNotification.InteractiveActionCallback
  ) {
    val currentState = deliveryConfigRepository.getConstraintStateById(parseUID(callback.messageId))
      ?: throw InvalidConstraintException("constraint@callbackId=${callback.messageId}", "constraint not found")

    log.debug("Updating constraint status based on notification interaction: " +
      "user = $user, status = ${callback.actionPerformed.value}")

    deliveryConfigRepository.storeConstraintState(
      currentState.copy(
        status = ConstraintStatus.valueOf(callback.actionPerformed.value),
        judgedAt = Instant.now(),
        judgedBy = user
      )
    )
  }
}
