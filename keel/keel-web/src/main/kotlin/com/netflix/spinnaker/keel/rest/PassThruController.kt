package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.BaseServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.toActive
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.kork.exceptions.UserException
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * This controller passes through calls to downstream services (e.g., clouddriver).
 *
 * It's used only for testing.
 */
@RestController
@ConditionalOnProperty("tests.passthru", matchIfMissing = false)
@RequestMapping(path = ["/test"])
class PassThruController(
  private val cloudDriverService: CloudDriverService
) {

  @GetMapping(
    path = ["/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/serverGroups"]
  )
  fun getActiveServerGroups(
    @PathVariable("app") app: String,
    @PathVariable("account") account: String,
    @PathVariable("cluster") cluster: String,
    @PathVariable("cloudProvider") cloudProvider: String
  ): List<BaseServerGroup> =
    runBlocking {
      when (cloudProvider) {
        "aws" -> getActiveAwsServerGroups(app, account, cluster)
        "titus" -> getActiveTitusServerGroups(app, account, cluster)
        else -> throw UserException("Unknown cloud provider: $cloudProvider")
      }
    }

  suspend fun getActiveAwsServerGroups(app: String, account: String, cluster: String) =
    cloudDriverService
      .listServerGroups(DEFAULT_SERVICE_ACCOUNT, app, account, cluster, "aws")
      .let { c ->
        c.serverGroups
          .filterNot { it.disabled }
          .map { it.toActive(c.accountName) }
      }

  suspend fun getActiveTitusServerGroups(app: String, account: String, cluster: String) =
    cloudDriverService
      .listTitusServerGroups(DEFAULT_SERVICE_ACCOUNT, app, account, cluster, "titus")
      .let { c ->
        c.serverGroups
          .filterNot { it.disabled }
          .map { it.toActive() }
      }
}
