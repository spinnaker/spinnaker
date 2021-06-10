package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.services.ApplicationService
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class VerificationController(
  private val verificationRepository: ActionRepository,
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val applicationService: ApplicationService,
) {
  @PostMapping(
    path = ["/application/{application}/environment/{environment}/verifications"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  fun updateVerificationStatus(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @PathVariable("application") application: String,
    @PathVariable("environment") environment: String,
    @RequestBody payload: UpdateVerificationStatusRequest
  ) {
    if (payload.status !in listOf(OVERRIDE_PASS, OVERRIDE_FAIL)) {
      throw NotOverrideStatus()
    }

    ArtifactInEnvironmentContext(
      deliveryConfig = deliveryConfigRepository.getByApplication(application),
      environmentName = environment,
      artifactReference = payload.artifactReference,
      version = payload.artifactVersion
    ).apply {
      verificationRepository.updateState(
        context = this,
        action = verification(payload.verificationId) ?: throw InvalidVerificationId(payload.verificationId, this),
        status = payload.status,
        mapOf("overriddenBy" to user, "comment" to payload.comment)
      )
    }
  }

  data class UpdateVerificationStatusRequest(
    val verificationId: String,
    val artifactReference: String,
    val artifactVersion: String,
    val status: ConstraintStatus,
    val comment: String?
  )

  @ResponseStatus(UNPROCESSABLE_ENTITY)
  private class NotOverrideStatus :
    RuntimeException("Only override statuses may be set via this endpoint.")

  @PostMapping(
    path = ["/application/{application}/environment/{environment}/verifications/retry"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  fun retryVerification(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @PathVariable("application") application: String,
    @PathVariable("environment") environment: String,
    @RequestBody payload: RetryVerificationRequest
  ) {
    applicationService.retryArtifactVersionAction(application = application,
                                                  environment = environment,
                                                  artifactReference = payload.artifactReference,
                                                  artifactVersion = payload.artifactVersion,
                                                  actionType = ActionType.VERIFICATION,
                                                  actionId = payload.verificationId,
                                                  user = user)
  }

  data class RetryVerificationRequest(
    val verificationId: String,
    val artifactReference: String,
    val artifactVersion: String
  )

  @ResponseStatus(CONFLICT)
  private class VerificationIncomplete :
    IllegalStateException("Verifications may only be retried once complete.")

  @ResponseStatus(NOT_FOUND)
  private class InvalidVerificationId(id: String, context: ArtifactInEnvironmentContext) :
    IllegalStateException("Unknown verification id: $id. Expecting one of: ${context.verifications.map { it.id }}")
}
