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
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.events.CreateEvent
import com.netflix.spinnaker.keel.events.DeleteEvent
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.KEEL_TAG_NAME
import com.netflix.spinnaker.keel.tags.TagValue
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

  private val resourceRepository: ResourceRepository = InMemoryResourceRepository()

  // lazy so we don't have to give an unused response for every call
  private val resourcePersister = mockk<ResourcePersister>(relaxed = true)

  private val cloudDriverService = mockk<CloudDriverService>()
  private val clock = MutableClock()

  private val clusterName = ResourceName("ec2:cluster:test:ap-south-1:keel")
  private val clusterTagName = ResourceName("$KEEL_TAG_NAME_PREFIX:ec2:cluster:test:ap-south-1:keel")

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

  private val rCluster = Resource(
    apiVersion = SPINNAKER_API_V1.subApi("ec2"),
    metadata = ResourceMetadata(
      name = clusterName,
      uid = randomUID()
    ),
    kind = "cluster",
    spec = mapOf("fake" to "data")
  )

  private val rClusterTag = Resource(
    apiVersion = SPINNAKER_API_V1.subApi("tag"),
    metadata = ResourceMetadata(
      name = clusterTagName,
      uid = randomUID()
    ),
    kind = "keel-tag",
    spec = KeelTagSpec(
      clusterName.toString(),
      EntityRef("cluster", "keel", "keel", "ap-south-1", "test", "aws"),
      TagDesired(tag = EntityTag(
        value = TagValue(
          message = KEEL_TAG_MESSAGE,
          keelResourceId = clusterName.toString(),
          type = "notice"
        ),
        namespace = KEEL_TAG_NAMESPACE,
        valueType = "object",
        name = KEEL_TAG_NAME
      )
      )
    )
  )

  private val rClusterTagNotDesired = Resource(
    apiVersion = SPINNAKER_API_V1.subApi("tag"),
    metadata = ResourceMetadata(
      name = clusterTagName,
      uid = randomUID()
    ),
    kind = "keel-tag",
    spec = KeelTagSpec(
      clusterName.toString(),
      EntityRef("cluster", "keel", "keel", "ap-south-1", "test", "aws"),
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

    context("cluster created") {
      before {
        resourceRepository.store(rCluster)
      }

      after {
        (resourceRepository as InMemoryResourceRepository).dropAll()
      }

      test("cluster is tagged") {
        onCreateEvent(CreateEvent(clusterName))
        verify { resourcePersister.create(any()) }
      }
    }

    context("cluster deleted") {
      before {
        resourceRepository.store(rCluster)
        resourceRepository.store(rClusterTag)
      }

      every {
        resourcePersister.update(clusterTagName, any())
      } answers { Resource(arg(1), ResourceMetadata(clusterTagName, randomUID())) }

      after {
        (resourceRepository as InMemoryResourceRepository).dropAll()
      }

      test("removes cluster tag on delete") {
        onDeleteEvent(DeleteEvent(clusterName))

        verify { resourcePersister.update(clusterTagName, any()) }
      }
    }

    context("tags exist") {
      before {
        resourceRepository.store(rClusterTagNotDesired)
      }

      every {
        resourcePersister.delete(rClusterTagNotDesired.metadata.name)
      } returns rClusterTagNotDesired

      after {
        (resourceRepository as InMemoryResourceRepository).dropAll()
      }

      test("they're not removed if they're new") {
        removeTags()

        verify { resourcePersister.delete(rClusterTagNotDesired.metadata.name) wasNot Called }
      }

      test("they're removed if they're old") {
        clock.incrementBy(Duration.ofMinutes(61))
        removeTags()

        verify { resourcePersister.delete(rClusterTagNotDesired.metadata.name) }
      }
    }

    context("we only tag certain resources") {
      after {
        clearAllMocks()
      }

      test("we don't tag named images") {
        onCreateEvent(CreateEvent(ResourceName("bakery:image:keel")))
        verify { resourcePersister.create(any()) wasNot Called }
      }
      test("we don't tag tags") {
        onCreateEvent(CreateEvent(ResourceName("tag:keel-tag:ec2:cluster:test:us-west-2:keeldemo-test")))
        verify { resourcePersister.create(any()) wasNot Called }
      }

      test("we tag clbs") {
        onCreateEvent(CreateEvent(ResourceName("ec2:classic-load-balancer:test:us-east-1:keel-managed")))
        verify { resourcePersister.create(any()) }
      }

      test("we tag albs") {
        onCreateEvent(CreateEvent(ResourceName("ec2:application-load-balancer:test:us-east-1:keel-managed")))
        verify { resourcePersister.create(any()) }
      }

      test("we tag security groups") {
        onCreateEvent(CreateEvent(ResourceName("ec2:securityGroup:test:us-west-2:keel-managed")))
        verify { resourcePersister.create(any()) }
      }

      test("we tag clusters") {
        onCreateEvent(CreateEvent(ResourceName("ec2:cluster:test:us-west-2:keeldemo-test")))
        verify { resourcePersister.create(any()) }
      }
    }
  }
}
