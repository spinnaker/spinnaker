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

import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.InMemoryAssetRepository
import com.netflix.spinnaker.keel.plugin.CustomResourceDefinitionLocator
import com.netflix.spinnaker.keel.plugin.KeelPlugin
import com.netflix.spinnaker.kork.PlatformComponents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Clock
import javax.annotation.PostConstruct

private val DEFAULT_PROPS = mapOf(
  "netflix.environment" to "test",
  "netflix.account" to "\${netflix.environment}",
  "netflix.stack" to "test",
  "spring.config.location" to "\${user.home}/.spinnaker/",
  "spring.application.name" to "keel",
  "spring.config.name" to "spinnaker,\${spring.application.name}",
  "spring.profiles.active" to "\${netflix.environment},local"
)

@SpringBootApplication(
  scanBasePackages = [
    "com.netflix.spinnaker.config",
    "com.netflix.spinnaker.keel"
  ]
)
@Import(PlatformComponents::class)
class KeelApplication {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Bean
  @ConditionalOnMissingBean
  fun clock(): Clock = Clock.systemDefaultZone()

  @Bean
  @ConditionalOnMissingBean
  fun assetRepository(clock: Clock): AssetRepository = InMemoryAssetRepository(clock)

  @Bean
  @ConditionalOnMissingBean(CustomResourceDefinitionLocator::class)
  fun noCustomResourceDefinitions(): List<CustomResourceDefinitionLocator> = emptyList()

  @Autowired
  lateinit var assetRepository: AssetRepository

  @Autowired(required = false)
  var plugins: List<KeelPlugin> = emptyList()

  @PostConstruct
  fun initialStatus() {
    log.info("Using {} asset repository implementation", assetRepository.javaClass.simpleName)
    log.info("Using plugins: {}", plugins.joinToString { it.name })
  }
}

fun main(vararg args: String) {
  SpringApplicationBuilder()
    .properties(DEFAULT_PROPS)
    .sources<KeelApplication>()
    .run(*args)
}

inline fun <reified T> SpringApplicationBuilder.sources(): SpringApplicationBuilder =
  sources(T::class.java)
