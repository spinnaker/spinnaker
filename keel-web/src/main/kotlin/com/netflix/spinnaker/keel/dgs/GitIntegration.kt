package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.MdApplication
import com.netflix.spinnaker.keel.graphql.types.MdGitIntegration
import com.netflix.spinnaker.keel.graphql.types.MdUpdateGitIntegrationPayload
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter.Companion.DEFAULT_MANIFEST_PATH
import com.netflix.spinnaker.keel.scm.ScmUtils
import com.netflix.spinnaker.keel.upsert.DeliveryConfigUpserter
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
  private val deliveryConfigUpserter: DeliveryConfigUpserter,
  private val importer: DeliveryConfigImporter,
) {
  @DgsData.List(
    DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.GitIntegration),
    DgsData(parentType = DgsConstants.MD_APPLICATION.TYPE_NAME, field = DgsConstants.MD_APPLICATION.GitIntegration),
  )
  fun gitIntegration(dfe: DgsDataFetchingEnvironment): MdGitIntegration {
    val app: MdApplication = dfe.getSource()
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    return runBlocking {
      front50Service.applicationByName(app.name)
    }.toGitIntegration()
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.UpdateGitIntegration),
    DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.Md_updateGitIntegration),
  )
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
          importDeliveryConfig = payload.isEnabled ?: front50Application.managedDelivery?.importDeliveryConfig ?: false,
          manifestPath = payload.manifestPath ?: front50Application.managedDelivery?.manifestPath
        )
      )
    }
    return updatedFront50App.toGitIntegration()
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.ImportDeliveryConfig),
    DgsData(parentType = DgsConstants.MUTATION.TYPE_NAME, field = DgsConstants.MUTATION.Md_importDeliveryConfig),
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #application)
    and @authorizationSupport.hasServiceAccountAccess('APPLICATION', #application)"""
  )
  fun importDeliveryConfig(
    @InputArgument application: String,
  ): Boolean {
    val front50App = runBlocking {
      front50Cache.applicationByName(application, invalidateCache = true)
    }
    val defaultBranch = scmUtils.getDefaultBranch(front50App)
    val deliveryConfig =
      importer.import(
        front50App.repoType ?: throw DgsEntityNotFoundException("Repo type is undefined for application"),
        front50App.repoProjectKey ?: throw DgsEntityNotFoundException("Repo project is undefined for application"),
        front50App.repoSlug ?: throw DgsEntityNotFoundException("Repo slug is undefined for application"),
        front50App.managedDelivery?.manifestPath,
        "refs/heads/$defaultBranch"
      )
    deliveryConfigUpserter.upsertConfig(deliveryConfig)
    return true
  }

  private fun Application.toGitIntegration(): MdGitIntegration {
    try {
      scmUtils.getDefaultBranch(this)
    } catch (e: Exception) {
      throw DgsEntityNotFoundException("Unable to retrieve your app's git repo details. Please check the app config.")
    }.let { branch ->
      return MdGitIntegration(
        id = "${name}-git-integration",
        repository = "${repoProjectKey}/${repoSlug}",
        branch = branch,
        isEnabled = managedDelivery?.importDeliveryConfig,
        manifestPath = managedDelivery?.manifestPath ?: DEFAULT_MANIFEST_PATH,
        link = scmUtils.getBranchLink(repoType, repoProjectKey, repoSlug, branch),
      )
    }
  }
}
