package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.core.api.parseUID
import com.netflix.spinnaker.keel.echo.model.EchoNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import com.netflix.spinnaker.kork.exceptions.SystemException
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
  private val repository: KeelRepository,
  private val authorizationSupport: AuthorizationSupport
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostMapping(
    consumes = [MediaType.APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [MediaType.APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  // TODO(lfp): We might need an additional authentication method for interactive constraint
  //  approval outside of the Spinnaker UI, e.g. in Slack, since X-SPINNAKER-USER will be extracted from the Slack
  //  message and not provided by the UI. My plan is to include an OTP in the callback URL. Note that echo
  //  does token/signature verification of messages, so there's relatively tight security there, but we still trust
  //  the e-mail address without a user having actually authenticated with Spinnaker.
  fun handleInteractionCallback(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @RequestBody callback: EchoNotification.InteractiveActionCallback
  ) {
    val currentState = repository.getConstraintStateById(parseUID(callback.messageId))
      ?: throw SystemException("constraint@callbackId=${callback.messageId}", "constraint not found")

    authorizationSupport.checkApplicationPermission(
      Action.WRITE, TargetEntity.DELIVERY_CONFIG, currentState.deliveryConfigName)

    log.debug("Updating constraint status based on notification interaction: " +
      "user = $user, status = ${callback.actionPerformed.value}")

    repository
      .storeConstraintState(
        currentState.copy(
          status = ConstraintStatus.valueOf(callback.actionPerformed.value),
          judgedAt = Instant.now(),
          judgedBy = user
        )
      )
  }
}
