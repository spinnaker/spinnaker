package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
    @RequestBody status: UpdatedVerificationStatus
  ) {
    require(status.status in listOf(OVERRIDE_PASS, OVERRIDE_FAIL)) {
      "Only override statuses may be set via this endpoint."
    }
    VerificationContext(
      deliveryConfig = deliveryConfigRepository.getByApplication(application),
      environmentName = environment,
      artifactReference = status.artifactReference,
      version = status.artifactVersion
    ).apply {
      verificationRepository.updateState(
        context = this,
        verification = verification(status.verificationId),
        status = status.status,
        mapOf("overriddenBy" to user, "comment" to status.comment)
      )
    }
  }

  data class UpdatedVerificationStatus(
    val verificationId: String,
    val artifactReference: String,
    val artifactVersion: String,
    val status: ConstraintStatus,
    val comment: String?
  )
}
