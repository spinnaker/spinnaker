/*
 * Copyright 2024 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.kork.secrets.env

import com.netflix.spinnaker.kork.secrets.SecretReferenceResolverProvider
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.support.SpringFactoriesLoader

/**
 * Handles registration of resolved secrets from the environment into a new property source.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
class SecretResolvingEnvironmentPostProcessor : EnvironmentPostProcessor {
  override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
    val providers = SpringFactoriesLoader.loadFactories(SecretReferenceResolverProvider::class.java, application.classLoader)
    val resolvers = SecretReferenceResolvers(providers.mapNotNull { it.create(environment) })
    resolvers.registerResolvedSecrets(environment.propertySources)
  }
}
