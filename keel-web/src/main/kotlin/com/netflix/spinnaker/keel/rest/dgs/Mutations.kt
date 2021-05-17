package com.netflix.spinnaker.keel.rest.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.InputArgument
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.MANUAL_JUDGEMENT_CONSTRAINT_TYPE
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.graphql.types.MdArtifactVersionActionPayload
import com.netflix.spinnaker.keel.graphql.types.MdConstraintStatus
import com.netflix.spinnaker.keel.graphql.types.MdConstraintStatusPayload
import com.netflix.spinnaker.keel.graphql.types.MdMarkArtifactVersionAsGoodPayload
import com.netflix.spinnaker.keel.graphql.types.MdUnpinArtifactVersionPayload
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.services.ApplicationService
import org.springframework.web.bind.annotation.RequestHeader

@DgsComponent
class Mutations(
  private val applicationService: ApplicationService,
  private val actuationPauser: ActuationPauser,
) {

  @DgsData(parentType = "Mutation", field = "updateConstraintStatus")
  fun updateConstraintStatus(
    @InputArgument payload: MdConstraintStatusPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {

    try {
      return applicationService.updateConstraintStatus(
        user = user,
        application = payload.application,
        environment = payload.environment,
        status = payload.toUpdatedConstraintStatus()
      )
    } catch (e: InvalidConstraintException) {
      throw throw IllegalArgumentException(e.message)
    }
  }

  @DgsData(parentType = "Mutation", field = "toggleManagement")
  fun toggleManagement(
    @InputArgument application: String,
    @InputArgument isPaused: Boolean,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    if (isPaused) {
      actuationPauser.pauseApplication(application, user)
    } else {
      actuationPauser.resumeApplication(application, user)
    }
    return true
  }

  @DgsData(parentType = "Mutation", field = "pinArtifactVersion")
  fun pinArtifactVersion(
    @InputArgument payload: MdArtifactVersionActionPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    applicationService.pin(user, payload.application, payload.toEnvironmentArtifactPin())
    return true
  }

  @DgsData(parentType = "Mutation", field = "unpinArtifactVersion")
  fun unpinArtifactVersion(
    @InputArgument payload: MdUnpinArtifactVersionPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    applicationService.deletePin(
      user = user,
      application = payload.application,
      targetEnvironment = payload.environment,
      reference = payload.reference
    )
    return true
  }

  @DgsData(parentType = "Mutation", field = "markArtifactVersionAsBad")
  fun markArtifactVersionAsBad(
    @InputArgument payload: MdArtifactVersionActionPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    applicationService.markAsVetoedIn(user, payload.application, payload.toEnvironmentArtifactVeto(), true)
    return true
  }

  @DgsData(parentType = "Mutation", field = "markArtifactVersionAsGood")
  fun markArtifactVersionAsGood(
    @InputArgument payload: MdMarkArtifactVersionAsGoodPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    applicationService.deleteVeto(
      application = payload.application,
      targetEnvironment = payload.environment,
      reference = payload.reference,
      version = payload.version
    )
    return true
  }
}

fun MdConstraintStatusPayload.toUpdatedConstraintStatus(): UpdatedConstraintStatus =
  UpdatedConstraintStatus(
    type = type,
    artifactReference = reference,
    artifactVersion = version,
    status = status.toConstraintStatus(type)
  )

fun MdArtifactVersionActionPayload.toEnvironmentArtifactPin(): EnvironmentArtifactPin =
  EnvironmentArtifactPin(
    targetEnvironment = environment,
    reference = reference,
    version = version,
    comment = comment,
    pinnedBy = null
  )

fun MdArtifactVersionActionPayload.toEnvironmentArtifactVeto(): EnvironmentArtifactVeto =
  EnvironmentArtifactVeto(
    targetEnvironment = environment,
    reference = reference,
    version = version,
    comment = comment,
    vetoedBy = null
  )


fun MdConstraintStatus.toConstraintStatus(constraintType: String): ConstraintStatus =
  when (this) {
    MdConstraintStatus.FAIL -> ConstraintStatus.FAIL
    MdConstraintStatus.FORCE_PASS -> if (constraintType == MANUAL_JUDGEMENT_CONSTRAINT_TYPE) {
      ConstraintStatus.PASS
    } else {
      ConstraintStatus.OVERRIDE_PASS
    }
    MdConstraintStatus.PASS -> ConstraintStatus.PASS
    MdConstraintStatus.PENDING -> ConstraintStatus.PENDING
    else -> throw IllegalArgumentException("Invalid constraint status")
  }

