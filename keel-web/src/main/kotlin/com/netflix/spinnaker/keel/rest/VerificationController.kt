package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.springframework.http.HttpStatus.CONFLICT
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
  private val verificationRepository: VerificationRepository,
  private val deliveryConfigRepository: DeliveryConfigRepository
) {
  @PostMapping(
    path = ["/{application}/environment/{environment}/verifications"],
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

    VerificationContext(
      deliveryConfig = deliveryConfigRepository.getByApplication(application),
      environmentName = environment,
      artifactReference = payload.artifactReference,
      version = payload.artifactVersion
    ).apply {
      verificationRepository.updateState(
        context = this,
        verification = verification(payload.verificationId),
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
    path = ["/{application}/environment/{environment}/verifications/retry"],
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
    VerificationContext(
      deliveryConfig = deliveryConfigRepository.getByApplication(application),
      environmentName = environment,
      artifactReference = payload.artifactReference,
      version = payload.artifactVersion
    ).apply {
      verificationRepository.getState(
        context = this,
        verification = verification(payload.verificationId)
      )?.apply {
        if (!status.complete) throw VerificationIncomplete()
      }

      verificationRepository.updateState(
        context = this,
        verification = verification(payload.verificationId),
        status = NOT_EVALUATED,
        mapOf("retryRequestedBy" to user)
      )
    }
  }

  data class RetryVerificationRequest(
    val verificationId: String,
    val artifactReference: String,
    val artifactVersion: String
  )

  @ResponseStatus(CONFLICT)
  private class VerificationIncomplete :
    IllegalStateException("Verifications may only be retried once complete.")
}
