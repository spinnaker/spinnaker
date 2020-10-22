package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.titus.exceptions.AwsAccountConfigurationException
import com.netflix.spinnaker.keel.titus.exceptions.TitusAccountConfigurationException
import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * A [Resolver] that looks up the needed titus default container attributes and
 * populates them, if they are not specified by the user.
 */
@Component
@EnableConfigurationProperties(DefaultContainerAttributes::class)
class ContainerAttributesResolver(
  val defaults: DefaultContainerAttributes,
  val cloudDriverService: CloudDriverService,
  val springEnv: Environment
) : Resolver<TitusClusterSpec> {

  private val enabled: Boolean
    get() = springEnv.getProperty("keel.titus.resolvers.container-attributes.enabled", Boolean::class.java, true)

  override val supportedKind = TITUS_CLUSTER_V1

  override fun invoke(resource: Resource<TitusClusterSpec>): Resource<TitusClusterSpec> {
    if (!enabled) {
      return resource
    }

    val topLevelAttrs = resource.spec.defaults.containerAttributes?.toMutableMap() ?: mutableMapOf()
    val overrides = resource.spec.overrides.toMutableMap()

    val account = resource.spec.locations.account
    // account key will be the same for all regions, so add it to the top level
    if (!keyPresentInAllRegions(resource, defaults.getAccountKey())) {
      val awsAccountId = getAwsAccountId(account)
      topLevelAttrs.putIfAbsent(defaults.getAccountKey(), awsAccountId)
    }

    if (!keyPresentInAllRegions(resource, defaults.getSubnetKey())){
      val regions = resource.spec.locations.regions.map { it.name }
      regions.forEach { region ->
        var override = overrides[region] ?: TitusServerGroupSpec()
        val subnetDefault = mapOf(defaults.getSubnetKey() to defaults.getSubnetValue(account, region))
        val containerAttributes = override.containerAttributes ?: mutableMapOf()
        override = if (!containerAttributes.containsKey(defaults.getSubnetKey())) {
          override.copy(containerAttributes = containerAttributes + subnetDefault)
        } else {
          override.copy(containerAttributes = containerAttributes)
        }

        overrides[region] = override
      }
    }
    val resourceDefaults = resource.spec.defaults.copy(containerAttributes = topLevelAttrs)
    val newSpec = resource.spec.copy(overrides = overrides, _defaults = resourceDefaults)
    return resource.copy(spec = newSpec)
  }

  /**
   * Returns true if when resolved, each region contains the desired key
   */
  private fun keyPresentInAllRegions(resource: Resource<TitusClusterSpec>, key: String): Boolean {
    val regions = resource.spec.locations.regions.map { it.name }
    var allPresent = true
    regions.forEach{ region ->
      val resolvedAttrs = resource.spec.resolveContainerAttributes(region)
      if (!resolvedAttrs.containsKey(key)) {
        allPresent = false
      }
    }
    return allPresent
  }

  private fun getAwsAccountId(titusAccount: String): String =
    runBlocking {
      val awsName = getAwsAccountNameForTitusAccount(titusAccount)
      getAccountId(awsName)
    }

  private suspend fun getAwsAccountNameForTitusAccount(titusAccount: String): String =
    cloudDriverService.getAccountInformation(titusAccount, DEFAULT_SERVICE_ACCOUNT)["awsAccount"]?.toString()
      ?: throw TitusAccountConfigurationException(titusAccount, "awsAccount")

  private suspend fun getAccountId(accountName: String): String =
    cloudDriverService.getAccountInformation(accountName, DEFAULT_SERVICE_ACCOUNT)["accountId"]?.toString()
      ?: throw AwsAccountConfigurationException(accountName, "accountId")
}
