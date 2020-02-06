/*
 * Copyright 2019 Netflix, Inc.
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
 */

package com.netflix.spinnaker.config

import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties
import com.netflix.spinnaker.front50.migrations.StorageServiceMigrator
import com.netflix.spinnaker.front50.model.CompositeStorageService
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.kork.web.context.AuthenticatedRequestContextProvider
import com.netflix.spinnaker.kork.web.context.RequestContextProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectThat
import strikt.assertions.isA

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [CompositeStorageServiceConfigurationTestApp::class],
  properties = ["spring.application.name=front50"]
  )
internal class CompositeStorageServiceConfigurationTests {

  @Autowired
  lateinit var compositeStorageService: CompositeStorageService

  @Autowired
  lateinit var storageServiceMigrator: StorageServiceMigrator

  @Autowired
  lateinit var sqlStorageService: SqlStorageService

  @Autowired
  lateinit var secondarySqlStorageService: SqlStorageService

  @Test
  fun `should wire up composite storage service with 2 sql storage services`() {
    expectThat(compositeStorageService).isA<CompositeStorageService>()
    expectThat(storageServiceMigrator).isA<StorageServiceMigrator>()
    expectThat(sqlStorageService).isA<SqlStorageService>()
    expectThat(secondarySqlStorageService).isA<SqlStorageService>()
  }
}

@SpringBootApplication
@Import(CompositeStorageServiceConfiguration::class, SqlConfiguration::class)
@EnableConfigurationProperties(StorageServiceConfigurationProperties::class)
internal class CompositeStorageServiceConfigurationTestApp {
  @Bean
  @ConditionalOnMissingBean
  fun contextProvider(): RequestContextProvider = AuthenticatedRequestContextProvider()
}
