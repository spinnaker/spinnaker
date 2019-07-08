/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.tag.EntityTags
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

internal object CompositeStorageServiceTests : JUnit5Minutests {
  val dynamicConfigService: DynamicConfigService = mockk(relaxUnitFun = true)
  val primary: StorageService = mockk(relaxUnitFun = true)
  val previous: StorageService = mockk(relaxUnitFun = true)

  val subject = CompositeStorageService(dynamicConfigService, NoopRegistry(), primary, previous)

  fun tests() = rootContext {
    after {
      clearMocks(dynamicConfigService, primary, previous)
    }

    context("loadObject()") {
      test("should always load EntityTags from 'primary'") {
        every {
          primary.loadObject<EntityTags>(ObjectType.ENTITY_TAGS, "id-entitytags001")
        } returns EntityTags().apply { id = "id-entitytags001" }

        every {
          dynamicConfigService.getConfig(Boolean::class.java, any(), any())
        } returns true

        expectThat(
          subject.loadObject<EntityTags>(ObjectType.ENTITY_TAGS, "id-entitytags001").id
        ).isEqualTo("id-entitytags001")

        verifyAll {
          primary.loadObject<Timestamped>(ObjectType.ENTITY_TAGS, "id-entitytags001")
          previous wasNot Called
        }
      }

      test("should favor 'primary'") {
        every {
          primary.loadObject<Application>(ObjectType.APPLICATION, "application001")
        } returns Application().apply { name = "application001" }

        every {
          dynamicConfigService.getConfig(Boolean::class.java, any(), any())
        } returns true

        expectThat(
          subject.loadObject<Application>(ObjectType.APPLICATION, "application001").id
        ).isEqualTo("application001")

        verifyAll {
          primary.loadObject<Timestamped>(ObjectType.APPLICATION, "application001")
          previous wasNot Called
        }
      }

      test("should fallback to 'previous' when 'primary' fails") {
        every {
          primary.loadObject<Application>(ObjectType.APPLICATION, "application001")
        } throws NotFoundException("Object not found")

        every {
          previous.loadObject<Application>(ObjectType.APPLICATION, "application001")
        } returns Application().apply { name = "application001" }

        every {
          dynamicConfigService.getConfig(Boolean::class.java, any(), any())
        } returns true

        expectThat(
          subject.loadObject<Application>(ObjectType.APPLICATION, "application001").id
        ).isEqualTo("application001")

        verifyAll {
          primary.loadObject<Timestamped>(ObjectType.APPLICATION, "application001")
          previous.loadObject<Timestamped>(ObjectType.APPLICATION, "application001")
        }
      }

      test("should propagate exception if 'primary' fails and 'previous' not enabled") {
        every {
          primary.loadObject<Application>(ObjectType.APPLICATION, "application001")
        } throws NotFoundException("Object not found")

        every {
          dynamicConfigService.getConfig(Boolean::class.java, match { it.contains("primary") }, any())
        } returns true

        every {
          dynamicConfigService.getConfig(Boolean::class.java, match { it.contains("previous") }, any())
        } returns false

        expectThrows<NotFoundException> {
          subject.loadObject<Application>(ObjectType.APPLICATION, "application001").id
        }

        verifyAll {
          primary.loadObject<Timestamped>(ObjectType.APPLICATION, "application001")
          previous wasNot Called
        }
      }
    }
  }
}
