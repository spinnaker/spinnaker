package com.netflix.spinnaker.keel.rest.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.InputArgument
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.MdApplication
import com.netflix.spinnaker.keel.graphql.types.MdGitIntegration
import com.netflix.spinnaker.keel.graphql.types.MdUpdateGitIntegrationPayload
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.igor.getDefaultBranch
import kotlinx.coroutines.runBlocking
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Fetches details about the application's git integration
 */
@DgsComponent
class GitIntegration(
  private val front50Service: Front50Service,
  private val scmService: ScmService,
  private val authorizationSupport: AuthorizationSupport,
) {

  @DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.GitIntegration)
  fun gitIntegration(dfe: DgsDataFetchingEnvironment): MdGitIntegration {
    val app: MdApplication = dfe.getSource()
    return runBlocking {
      front50Service.applicationByName(app.name)
    }.toGitIntegration()
  }

  @DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.UpdateGitIntegration)
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #payload.application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #payload.application)"""
  )
  fun updateGitIntegration(
    @InputArgument payload: MdUpdateGitIntegrationPayload,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): MdGitIntegration {
    return runBlocking {
      front50Service.updateApplication(
        payload.application,
        user,
        Application(
          name = payload.application,
          email = user,
          managedDelivery = ManagedDeliveryConfig(importDeliveryConfig = payload.isEnabled)
        )
      )
    }.toGitIntegration()
  }

  private fun Application.toGitIntegration() = MdGitIntegration(
    id = "${name}-git-integration",
    repository = "${repoProjectKey}/${repoSlug}",
    branch = getDefaultBranch(scmService),
    isEnabled = managedDelivery?.importDeliveryConfig
  )
}
