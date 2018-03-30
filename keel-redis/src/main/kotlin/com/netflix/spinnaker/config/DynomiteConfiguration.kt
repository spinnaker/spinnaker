/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.config

import com.netflix.discovery.DiscoveryClient
import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.connectionpool.HostSupplier
import com.netflix.dyno.connectionpool.TokenMapSupplier
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.dyno.connectionpool.impl.lb.HostToken
import com.netflix.dyno.jedis.DynoJedisClient
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
@ConditionalOnProperty("dynomite.enabled")
@EnableConfigurationProperties(DynomiteConfigurationProperties::class)
open class DynomiteConfiguration {

  @Bean
  @ConfigurationProperties("dynomite.main.connectionPool")
  open fun mainConnectionPoolConfigurationImpl(mainDynomiteConfigurationProperties: DynomiteConfigurationProperties): ConnectionPoolConfigurationImpl {
    return ConnectionPoolConfigurationImpl(mainDynomiteConfigurationProperties.applicationName)
  }

  @Bean(destroyMethod = "stopClient")
  open fun mainDynoJedisClient(mainDynomiteConfigurationProperties: DynomiteConfigurationProperties,
                               mainConnectionPoolConfiguration: ConnectionPoolConfigurationImpl,
                               discoveryClient: Optional<DiscoveryClient>): DynoJedisClient
    = createDynoJedisClient(mainDynomiteConfigurationProperties, mainConnectionPoolConfiguration, discoveryClient)

  @Bean(name = ["mainRedisClient"]) open fun dynomiteClientDelegate(dynoJedisClient: DynoJedisClient): DynomiteClientDelegate {
    return DynomiteClientDelegate(dynoJedisClient)
  }

  // TODO rz - dyno previous client
  @Bean(name = ["previousRedisClient"]) open fun previousDynomiteClientDelegate(): DynomiteClientDelegate? = null

  private fun createDynoJedisClient(dynomiteConfigurationProperties: DynomiteConfigurationProperties,
                                    connectionPoolConfiguration: ConnectionPoolConfigurationImpl,
                                    discoveryClient: Optional<DiscoveryClient>): DynoJedisClient {
    val builder = DynoJedisClient.Builder()
      .withApplicationName(dynomiteConfigurationProperties.applicationName)
      .withDynomiteClusterName(dynomiteConfigurationProperties.clusterName)

    return discoveryClient.map({ dc ->
      @Suppress("DEPRECATION")
      builder.withDiscoveryClient(dc)
        .withCPConfig(connectionPoolConfiguration)
    }).orElseGet({
      connectionPoolConfiguration
        .withTokenSupplier( StaticTokenMapSupplier(dynomiteConfigurationProperties.getDynoHostTokens()))
        .setLocalDataCenter(dynomiteConfigurationProperties.localDataCenter)
        .setLocalRack(dynomiteConfigurationProperties.localRack)

      builder
        .withHostSupplier( StaticHostSupplier(dynomiteConfigurationProperties.getDynoHosts()))
        .withCPConfig(connectionPoolConfiguration)
    }).build()
  }

  // TODO rz - HealthIndicator

  class StaticHostSupplier(private val hosts: MutableCollection<Host>) : HostSupplier {
    override fun getHosts() = hosts
  }

  class StaticTokenMapSupplier(private val ht: MutableList<HostToken>) : TokenMapSupplier {
    override fun getTokenForHost(host: Host, activeHosts: MutableSet<Host>) = ht.find { it.host == host }!!
    override fun getTokens(activeHosts: MutableSet<Host>): MutableList<HostToken> = ht
  }
}
