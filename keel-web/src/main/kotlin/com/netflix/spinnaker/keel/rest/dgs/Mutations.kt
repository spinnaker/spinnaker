package com.netflix.spinnaker.keel.rest.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.MANUAL_JUDGEMENT_CONSTRAINT_TYPE
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.graphql.types.MdAction
import com.netflix.spinnaker.keel.graphql.types.MdActionType
import com.netflix.spinnaker.keel.graphql.types.MdArtifactVersionActionPayload
import com.netflix.spinnaker.keel.graphql.types.MdConstraintStatus
import com.netflix.spinnaker.keel.graphql.types.MdConstraintStatusPayload
import com.netflix.spinnaker.keel.graphql.types.MdMarkArtifactVersionAsGoodPayload
import com.netflix.spinnaker.keel.graphql.types.MdRetryArtifactActionPayload
import com.netflix.spinnaker.keel.graphql.types.MdUnpinArtifactVersionPayload
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.services.ApplicationService
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import org.springframework.web.bind.annotation.RequestHeader

@DgsComponent
class Mutations(
  private val applicationService: ApplicationService,
  private val actuationPauser: ActuationPauser,
  private val unhappyVeto: UnhappyVeto,
  private val deliveryConfigRepository: DeliveryConfigRepository
) {

  @DgsData(parentType = "Mutation", field = "recheckUnhappyResource")
  fun recheckUnhappyResource(
    @InputArgument resourceId: String
  ) {
    unhappyVeto.clearVeto(resourceId)
  }

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
        status = payload.toUpdatedConstraintStatus(),
      )
    } catch (e: InvalidConstraintException) {
      throw throw IllegalArgumentException(e.message)
    }
  }

  @DgsData(parentType = "Mutation", field = "toggleManagement")
  fun toggleManagement(
    @InputArgument application: String,
    @InputArgument isPaused: Boolean,
    @InputArgument comment: String? = null,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    if (isPaused) {
      actuationPauser.pauseApplication(application, user, comment)
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

  @DgsData(parentType = "Mutation", field = "retryArtifactVersionAction")
  fun retryArtifactVersionAction(
    @InputArgument payload: MdRetryArtifactActionPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): MdAction {

    val actionType = ActionType.valueOf(payload.actionType.name)
    val newStatus = applicationService.retryArtifactVersionAction(application = payload.application,
                                                                  environment = payload.environment,
                                                                  artifactReference = payload.reference,
                                                                  artifactVersion = payload.version,
                                                                  actionType = actionType,
                                                                  actionId = payload.actionId,
                                                                  user = user)

    ArtifactInEnvironmentContext(
      deliveryConfig = deliveryConfigRepository.getByApplication(payload.application),
      environmentName = payload.environment,
      artifactReference = payload.reference,
      version = payload.version
    ).apply {
      return MdAction(
        id = this.getMdActionId(actionType, payload.actionId),
        actionId = payload.actionId,
        type = payload.actionId, // Deprecated - TODO: remove this
        actionType = payload.actionType,
        status = newStatus.toDgsActionStatus(),
      )
    }
  }
}

fun MdConstraintStatusPayload.toUpdatedConstraintStatus(): UpdatedConstraintStatus =
  UpdatedConstraintStatus(
    type = type,
    artifactReference = reference,
    artifactVersion = version,
    status = status.toConstraintStatus(),
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


fun MdConstraintStatus.toConstraintStatus(): ConstraintStatus =
  when (this) {
    MdConstraintStatus.FAIL -> ConstraintStatus.FAIL
    MdConstraintStatus.FORCE_PASS -> ConstraintStatus.OVERRIDE_PASS
    MdConstraintStatus.PASS -> ConstraintStatus.PASS
    MdConstraintStatus.PENDING -> ConstraintStatus.PENDING
    else -> throw IllegalArgumentException("Invalid constraint status")
  }

