package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.InputArgument
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.MdAction
import com.netflix.spinnaker.keel.graphql.types.MdArtifactVersionActionPayload
import com.netflix.spinnaker.keel.graphql.types.MdConstraintStatus
import com.netflix.spinnaker.keel.graphql.types.MdConstraintStatusPayload
import com.netflix.spinnaker.keel.graphql.types.MdDismissNotificationPayload
import com.netflix.spinnaker.keel.graphql.types.MdMarkArtifactVersionAsGoodPayload
import com.netflix.spinnaker.keel.graphql.types.MdRetryArtifactActionPayload
import com.netflix.spinnaker.keel.graphql.types.MdUnpinArtifactVersionPayload
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.services.ApplicationService
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import de.huxhorn.sulky.ulid.ULID
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Component responsible for encapsulating GraphQL mutations exposed on the API.
 */
@DgsComponent
class Mutations(
  private val applicationService: ApplicationService,
  private val actuationPauser: ActuationPauser,
  private val unhappyVeto: UnhappyVeto,
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val notificationRepository: DismissibleNotificationRepository,
  private val authorizationSupport: AuthorizationSupport,
) {

  companion object {
    private val log by lazy { LoggerFactory.getLogger(Mutations::class.java) }
  }

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = "recheckUnhappyResource")
  fun recheckUnhappyResource(
    @InputArgument resourceId: String
  ) {
    unhappyVeto.clearVeto(resourceId)
  }

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.UpdateConstraintStatus)
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #payload.application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #payload.application)"""
  )
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

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.ToggleManagement)
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)")
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

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.PinArtifactVersion)
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #payload.application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #payload.application)"""
  )
  fun pinArtifactVersion(
    @InputArgument payload: MdArtifactVersionActionPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    applicationService.pin(user, payload.application, payload.toEnvironmentArtifactPin())
    return true
  }

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.UnpinArtifactVersion)
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #payload.application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #payload.application)"""
  )
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

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.MarkArtifactVersionAsBad)
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #payload.application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #payload.application)"""
  )
  fun markArtifactVersionAsBad(
    @InputArgument payload: MdArtifactVersionActionPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    applicationService.markAsVetoedIn(user, payload.application, payload.toEnvironmentArtifactVeto(), true)
    return true
  }

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.MarkArtifactVersionAsGood)
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #payload.application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #payload.application)"""
  )
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

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.RetryArtifactVersionAction)
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #payload.application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #payload.application)"""
  )
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

  /**
   * Dismisses a notification, given it's ID.
   */
  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.DismissNotification)
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #payload.application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #payload.application)"""
  )
  fun dismissNotification(
    @InputArgument payload: MdDismissNotificationPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): Boolean {
    log.debug("Dismissing notification with ID=${payload.id} (by user $user)")
    return notificationRepository.dismissNotificationById(payload.application, ULID.parseULID(payload.id), user)
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

