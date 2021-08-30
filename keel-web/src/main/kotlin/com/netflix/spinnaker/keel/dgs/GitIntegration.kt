package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.InputArgument
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.MdApplication
import com.netflix.spinnaker.keel.graphql.types.MdGitIntegration
import com.netflix.spinnaker.keel.graphql.types.MdUpdateGitIntegrationPayload
import com.netflix.spinnaker.keel.scm.ScmUtils
import kotlinx.coroutines.runBlocking
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Fetches details about the application's git integration
 */
@DgsComponent
class GitIntegration(
  private val front50Service: Front50Service,
  private val front50Cache: Front50Cache,
  private val authorizationSupport: AuthorizationSupport,
  private val applicationFetcherSupport: ApplicationFetcherSupport,
  private val scmUtils: ScmUtils,
) {
  @DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.GitIntegration)
  fun gitIntegration(dfe: DgsDataFetchingEnvironment): MdGitIntegration {
    val app: MdApplication = dfe.getSource()
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
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
    val front50Application = runBlocking {
      front50Cache.applicationByName(payload.application)
    }
    val updatedFront50App = runBlocking {
      front50Cache.updateManagedDeliveryConfig(
        front50Application,
        user,
        ManagedDeliveryConfig(
          importDeliveryConfig = payload.isEnabled ?: front50Application.managedDelivery.importDeliveryConfig,
          manifestPath = payload.manifestPath ?: front50Application.managedDelivery.manifestPath
        )
      )
    }
    return updatedFront50App.toGitIntegration()
  }

  private fun Application.toGitIntegration(): MdGitIntegration {
    val branch = scmUtils.getDefaultBranch(this)
    return MdGitIntegration(
      id = "${name}-git-integration",
      repository = "${repoProjectKey}/${repoSlug}",
      branch = branch,
      isEnabled = managedDelivery.importDeliveryConfig,
      manifestPath = managedDelivery.manifestPath,
      link = scmUtils.getBranchLink(repoType, repoProjectKey, repoSlug, branch),
    )
  }
}
