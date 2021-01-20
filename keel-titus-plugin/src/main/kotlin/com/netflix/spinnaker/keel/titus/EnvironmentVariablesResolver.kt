package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * A resolver that adds the Spinnaker account as an evn variable
 * because this happens already in clouddriver
 * https://github.com/spinnaker/clouddriver/pull/5185/files
 */
@Component
class EnvironmentVariablesResolver(
  val springEnv: Environment
) : Resolver<TitusClusterSpec> {

  private val enabled: Boolean
    get() = springEnv.getProperty("keel.titus.resolvers.environment.enabled", Boolean::class.java, true)

  override val supportedKind = TITUS_CLUSTER_V1

  override fun invoke(resource: Resource<TitusClusterSpec>): Resource<TitusClusterSpec> {
    if (!enabled) {
      return resource
    }

    val env = resource.spec.defaults.env?.toMutableMap() ?: mutableMapOf()
    val account = resource.spec.locations.account
    env["SPINNAKER_ACCOUNT"] = account

    val resourceDefaults = resource.spec.defaults.copy(env = env)
    val newSpec = resource.spec.copy(_defaults = resourceDefaults)
    return resource.copy(spec = newSpec)
  }
}
