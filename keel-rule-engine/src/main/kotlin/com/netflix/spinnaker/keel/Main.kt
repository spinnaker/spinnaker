/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.InMemoryAssetRepository
import com.netflix.spinnaker.keel.processing.ConvergeAsset
import com.netflix.spinnaker.keel.processing.ValidateAssetTree
import com.netflix.spinnaker.keel.registry.InMemoryPluginRepository
import com.netflix.spinnaker.keel.registry.PluginRepository
import com.netflix.spinnaker.kork.PlatformComponents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import java.time.Clock
import javax.annotation.PostConstruct

object MainDefaults {
  val PROPS = mapOf(
    "netflix.environment" to "test",
    "netflix.account" to "\${netflix.environment}",
    "netflix.stack" to "test",
    "spring.config.location" to "\${user.home}/.spinnaker/",
    "spring.application.name" to "keel",
    "spring.config.name" to "spinnaker,\${spring.application.name}",
    "spring.profiles.active" to "\${netflix.environment},local"
  )
}

@SpringBootApplication
@ComponentScan(basePackages = [
  "com.netflix.spinnaker.config",
  "com.netflix.spinnaker.keel"
])
@Import(PlatformComponents::class)
class RuleEngineApp {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Bean
  @ConditionalOnMissingBean(AssetRepository::class)
  fun assetRepository(clock: Clock) = InMemoryAssetRepository(clock)

  @Bean
  @ConditionalOnMissingBean(PluginRepository::class)
  fun pluginRepository() = InMemoryPluginRepository()

  @Autowired
  lateinit var pluginRepository: PluginRepository

  @Autowired
  lateinit var assetRepository: AssetRepository

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @PostConstruct
  fun initialStatus() {
    log.info("Using {} plugin repository implementation", pluginRepository.javaClass.simpleName)
    log.info("Using {} asset repository implementation", assetRepository.javaClass.simpleName)
  }

  @PostConstruct
  fun registerKeikoMessageTypes() {
    val messageTypes = arrayOf(
      ValidateAssetTree::class.java,
      ConvergeAsset::class.java
    )
    messageTypes.forEach {
      log.info("Registering message type {} on {}", it.simpleName, objectMapper)
    }
    objectMapper.registerSubtypes(*messageTypes)
  }
}

fun main(vararg args: String) {
  SpringApplicationBuilder()
    .properties(MainDefaults.PROPS)
    .sources<RuleEngineApp>()
    .run(*args)
}

inline fun <reified T> SpringApplicationBuilder.sources(): SpringApplicationBuilder =
  sources(T::class.java)
