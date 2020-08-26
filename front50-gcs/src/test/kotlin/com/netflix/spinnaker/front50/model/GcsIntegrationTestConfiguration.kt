/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.front50.model

import com.google.auth.Credentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.config.GcsProperties
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties
import com.netflix.spinnnaker.front50.model.FakeStorageRpcFactory
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.circuitbreaker.internal.InMemoryCircuitBreakerRegistry
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@EnableConfigurationProperties(StorageServiceConfigurationProperties::class)
@TestConfiguration
class GcsIntegrationTestConfiguration {
  private val clock = Clock.fixed(Instant.ofEpochSecond(629528400L), ZoneOffset.UTC)

  @Bean
  fun noopRegistry(): Registry = NoopRegistry()

  @Bean
  @Primary
  @Qualifier("gcsCredentials") fun gcsCredentials(): Credentials = mockk()

  @Bean
  fun circuitBreakerRegistry(): CircuitBreakerRegistry = InMemoryCircuitBreakerRegistry()

  @Bean
  fun storage(properties: GcsProperties): Storage {
    return StorageOptions.newBuilder().setServiceRpcFactory(FakeStorageRpcFactory(clock)).build().service
  }
}
