package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.spinnaker.keel.actuation.ExecutionSummaryService
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.MdArtifact
import com.netflix.spinnaker.keel.graphql.types.MdExecutionSummary
import com.netflix.spinnaker.keel.graphql.types.MdResource
import com.netflix.spinnaker.keel.graphql.types.MdResourceActuationState
import com.netflix.spinnaker.keel.graphql.types.MdResourceActuationStatus
import com.netflix.spinnaker.keel.graphql.types.MdResourceTask
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.scm.ScmUtils
import com.netflix.spinnaker.keel.services.ResourceStatusService
import graphql.schema.DataFetchingEnvironment

/**
 * Fetches details about resources, as defined in [schema.graphql]
 */
@DgsComponent
class ResourceFetcher(
  private val authorizationSupport: AuthorizationSupport,
  private val keelRepository: KeelRepository,
  private val resourceStatusService: ResourceStatusService,
  private val actuationPauser: ActuationPauser,
  private val artifactVersionLinks: ArtifactVersionLinks,
  private val applicationFetcherSupport: ApplicationFetcherSupport,
  private val notificationRepository: DismissibleNotificationRepository,
  private val scmUtils: ScmUtils,
  private val executionSummaryService: ExecutionSummaryService,
  private val taskTrackingRepository: TaskTrackingRepository
) {
  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACT.TYPE_NAME, field = DgsConstants.MDARTIFACT.Resources),
    DgsData(parentType = DgsConstants.MD_ARTIFACT.TYPE_NAME, field = DgsConstants.MD_ARTIFACT.Resources),
  )
  fun artifactResources(dfe: DataFetchingEnvironment): List<MdResource>? {
    val artifact: MdArtifact = dfe.getSource()
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    return artifact.environment?.let {
      config.resourcesUsing(artifact.reference, artifact.environment).map { it.toDgs(config, artifact.environment) }
    }
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDRESOURCE.TYPE_NAME, field = DgsConstants.MDRESOURCE.State),
    DgsData(parentType = DgsConstants.MD_RESOURCE.TYPE_NAME, field = DgsConstants.MD_RESOURCE.State),
  )
  fun resourceStatus(dfe: DgsDataFetchingEnvironment): MdResourceActuationState {
    val resource: MdResource = dfe.getSource()
    val state = resourceStatusService.getActuationState(resource.id)
    return MdResourceActuationState(
      resourceId = resource.id,
      status = MdResourceActuationStatus.valueOf(state.status.name),
      reason = state.reason,
      event = state.eventMessage
    )
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDRESOURCEACTUATIONSTATE.TYPE_NAME, field = DgsConstants.MDRESOURCEACTUATIONSTATE.Tasks),
    DgsData(parentType = DgsConstants.MD_RESOURCEACTUATIONSTATE.TYPE_NAME, field = DgsConstants.MD_RESOURCEACTUATIONSTATE.Tasks),
  )
  fun resourceTask(dfe: DgsDataFetchingEnvironment): List<MdResourceTask> {
    val resourcceState: MdResourceActuationState = dfe.getSource()
    val tasks = taskTrackingRepository.getLatestBatchOfTasks(resourceId = resourcceState.resourceId)
    return tasks.map { it.toDgs() }
  }


  @DgsData.List(
    DgsData(parentType = DgsConstants.MDRESOURCETASK.TYPE_NAME, field = DgsConstants.MDRESOURCETASK.Summary),
    DgsData(parentType = DgsConstants.MD_RESOURCETASK.TYPE_NAME, field = DgsConstants.MD_RESOURCETASK.Summary),
  )
  fun taskSummary(dfe: DgsDataFetchingEnvironment): MdExecutionSummary {
    val task: MdResourceTask = dfe.getSource()
    val summary = executionSummaryService.getSummary(task.id)
    return summary.toDgs()
  }

}
