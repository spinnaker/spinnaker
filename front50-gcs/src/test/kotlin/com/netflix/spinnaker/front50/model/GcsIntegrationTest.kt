/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnnaker.front50.model

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.config.GcsConfig
import com.netflix.spinnaker.front50.model.GcsIntegrationTestConfiguration
import com.netflix.spinnaker.front50.model.GcsStorageService
import com.netflix.spinnaker.front50.model.ObjectType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty

@ExtendWith(SpringExtension::class)
@ContextConfiguration(
  classes = [GcsConfig::class, GcsIntegrationTestConfiguration::class],
  initializers = [ConfigFileApplicationContextInitializer::class]
)
@TestPropertySource(properties = ["spring.config.location=classpath:minimal-gcs-account.yml"])
class GcsIntegrationTest {
  @Test
  fun startupTest(@Autowired storageService: GcsStorageService) {
    expectThat(storageService.listObjectKeys(ObjectType.PIPELINE)).isEmpty()
    storageService.storeObject(ObjectType.PIPELINE, "my-key", Pipeline())
    expectThat(storageService.listObjectKeys(ObjectType.PIPELINE)).hasSize(1)
  }
}
