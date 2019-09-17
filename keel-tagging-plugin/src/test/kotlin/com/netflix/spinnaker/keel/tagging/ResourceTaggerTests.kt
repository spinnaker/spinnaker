/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.tagging

import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeleted
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.KEEL_TAG_NAME
import com.netflix.spinnaker.keel.tags.TagValue
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration

internal class ResourceTaggerTests : JUnit5Minutests {

  private val resourceRepository = InMemoryResourceRepository()

  // lazy so we don't have to give an unused response for every call
  private val resourcePersister = mockk<ResourcePersister>(relaxed = true)

  private val cloudDriverService = mockk<CloudDriverService>()
  private val clock = MutableClock()

  private val serverGroupId = ResourceId("ec2:server-group:test:ap-south-1:keel")
  private val serverGroupTagId = ResourceId("$KEEL_TAG_ID_PREFIX:ec2:server-group:test:ap-south-1:keel")

  private val accounts: Set<Credential> = setOf(
    Credential(
      data = mapOf<String, Any>(
        "name" to "test",
        "type" to "aws",
        "accountId" to "00000000000",
        "cloudProvider" to "aws"
      )
    )
  )

  private val rServerGroup = resource(
    apiVersion = SPINNAKER_API_V1.subApi("ec2"),
    kind = "server-group",
    id = "test:ap-south-1:keel"
  )

  private val rServerGroupTag = resource(
    apiVersion = SPINNAKER_API_V1.subApi("tag"),
    kind = "keel-tag",
    spec = KeelTagSpec(
      keelId = serverGroupId,
      entityRef = EntityRef("server-group", "keel", "keel", "ap-south-1", "test", "1234", "aws"),
      tagState = TagDesired(tag = EntityTag(
        value = TagValue(
          message = KEEL_TAG_MESSAGE,
          keelResourceId = serverGroupId.toString(),
          type = "notice"
        ),
        namespace = KEEL_TAG_NAMESPACE,
        valueType = "object",
        name = KEEL_TAG_NAME
      ))
    )
  )

  private val rServerGroupTagNotDesired = resource(
    apiVersion = SPINNAKER_API_V1.subApi("tag"),
    kind = "keel-tag",
    spec = KeelTagSpec(
      serverGroupId,
      EntityRef("server-group", "keel", "keel", "ap-south-1", "test", "1234", "aws"),
      TagNotDesired(clock.millis())
    )
  )

  fun tests() = rootContext<ResourceTagger> {
    fixture {
      ResourceTagger(
        resourceRepository = resourceRepository,
        resourcePersister = resourcePersister,
        cloudDriverService = cloudDriverService,
        removedTagRetentionHours = 1,
        clock = clock
      )
    }

    coEvery {
      cloudDriverService.listCredentials()
    } returns accounts

    context("server group created") {
      before {
        resourceRepository.store(rServerGroup)
      }

      after {
        resourceRepository.dropAll()
      }

      test("server group is tagged") {
        onCreateEvent(ResourceCreated(rServerGroup))
        verify { resourcePersister.create<DummyResourceSpec>(any()) }
      }
    }

    context("server group deleted") {
      before {
        resourceRepository.store(rServerGroup)
        resourceRepository.store(rServerGroupTag)
      }

      every {
        resourcePersister.update<DummyResourceSpec>(serverGroupTagId, any())
      } answers {
        Resource(
          secondArg(),
          mapOf(
            "id" to serverGroupTagId.value,
            "uid" to randomUID(),
            "serviceAccount" to "keel@spinnaker",
            "application" to "keel"
          )
        )
      }

      after {
        resourceRepository.dropAll()
      }

      test("removes server group tag on delete") {
        onDeleteEvent(ResourceDeleted(rServerGroup, clock))

        verify { resourcePersister.update<DummyResourceSpec>(serverGroupTagId, any()) }
      }
    }

    context("tags exist") {
      before {
        resourceRepository.store(rServerGroupTagNotDesired)
      }

      every {
        resourcePersister.delete(rServerGroupTagNotDesired.id)
      } returns rServerGroupTagNotDesired

      after {
        resourceRepository.dropAll()
      }

      test("they're not removed if they're new") {
        removeTags()

        verify { resourcePersister.delete(rServerGroupTagNotDesired.id) wasNot Called }
      }

      test("they're removed if they're old") {
        clock.incrementBy(Duration.ofMinutes(61))
        removeTags()

        verify { resourcePersister.delete(rServerGroupTagNotDesired.id) }
      }
    }

    context("we only tag certain resources") {
      after {
        clearAllMocks()
      }

      test("we don't tag named images") {
        onCreateEvent(ResourceCreated(
          apiVersion = SPINNAKER_API_V1.subApi("bakery"),
          kind = "image",
          id = "bakery:image:keel",
          application = "keel",
          timestamp = clock.instant()
        ))
        verify { resourcePersister.create<DummyResourceSpec>(any()) wasNot Called }
      }
      test("we don't tag tags") {
        onCreateEvent(ResourceCreated(rServerGroupTag))
        verify { resourcePersister.create<DummyResourceSpec>(any()) wasNot Called }
      }

      test("we tag clbs") {
        onCreateEvent(ResourceCreated(
          apiVersion = SPINNAKER_API_V1.subApi("ec2"),
          kind = "classic-load-balancer",
          id = "ec2:classic-load-balancer:test:us-east-1:keel-managed",
          application = "keel",
          timestamp = clock.instant()
        ))
        verify { resourcePersister.create<DummyResourceSpec>(any()) }
      }

      test("we tag albs") {
        onCreateEvent(ResourceCreated(
          apiVersion = SPINNAKER_API_V1.subApi("ec2"),
          kind = "application-load-balancer",
          id = "ec2:application-load-balancer:test:us-east-1:keel-managed",
          application = "keel",
          timestamp = clock.instant()
        ))
        verify { resourcePersister.create<DummyResourceSpec>(any()) }
      }

      test("we tag security groups") {
        onCreateEvent(ResourceCreated(
          apiVersion = SPINNAKER_API_V1.subApi("ec2"),
          kind = "security-group",
          id = "ec2:security-group:test:us-west-2:keel-managed",
          application = "keel",
          timestamp = clock.instant()
        ))
        verify { resourcePersister.create<DummyResourceSpec>(any()) }
      }

      test("we tag server groups") {
        onCreateEvent(ResourceCreated(
          apiVersion = SPINNAKER_API_V1.subApi("ec2"),
          kind = "server-group",
          id = "ec2:server-group:test:us-west-2:keeldemo-test",
          application = "keel",
          timestamp = clock.instant()
        ))
        verify { resourcePersister.create<DummyResourceSpec>(any()) }
      }
    }
  }
}
