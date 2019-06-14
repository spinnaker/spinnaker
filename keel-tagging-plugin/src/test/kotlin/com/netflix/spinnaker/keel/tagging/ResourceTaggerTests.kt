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
import com.netflix.spinnaker.keel.events.DeleteEvent
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.KEEL_TAG_NAME
import com.netflix.spinnaker.keel.tags.TagValue
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expect
import strikt.assertions.isEqualTo

internal class ResourceTaggerTests : JUnit5Minutests {

  private val resourceRepository: ResourceRepository = InMemoryResourceRepository()
  private val resourcePersister = mockk<ResourcePersister>()
  private val cloudDriverService = mockk<CloudDriverService>()
  private val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)

  private val sgName = ResourceName("ec2:security-group:test:us-west-2:fnord")
  private val sgTagName = ResourceName("$KEEL_TAG_NAME_PREFIX:ec2:security-group:test:us-west-2:fnord")
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

  private val rSG = Resource(
    apiVersion = SPINNAKER_API_V1.subApi("ec2"),
    metadata = ResourceMetadata(
      name = sgName,
      uid = randomUID()
    ),
    kind = "security-group",
    spec = mapOf("fake" to "data")
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
        category = "notice",
        name = KEEL_TAG_NAME
      )
      )
    )
  )

  fun tests() = rootContext<ResourceTagger> {
    fixture {
      ResourceTagger(
        resourceRepository = resourceRepository,
        resourcePersister = resourcePersister,
        cloudDriverService = cloudDriverService,
        publisher = publisher
      )
    }

    coEvery {
      cloudDriverService.listCredentials()
    } returns accounts

    every {
      resourcePersister.create(any())
    } answers { Resource(arg(0), ResourceMetadata(ResourceName("this:is:a:name"), randomUID())) }

    context("tagger is disabled") {
      test("nothing happens") {
        checkResources()
        expect { that((resourceRepository as InMemoryResourceRepository).size()).isEqualTo(0) }
      }
    }

    context("everything tagged") {
      before {
        onApplicationUp()

        resourceRepository.store(rCluster)
        resourceRepository.store(rClusterTag)
      }

      after {
        onApplicationDown()
        (resourceRepository as InMemoryResourceRepository).dropAll()
      }

      test("nothing happens") {
        checkResources()
        verify { resourcePersister wasNot Called }
      }
    }

    context("sg not tagged") {
      before {
        onApplicationUp()

        resourceRepository.store(rSG)
        resourceRepository.store(rCluster)
        resourceRepository.store(rClusterTag)
      }

      after {
        onApplicationDown()
        (resourceRepository as InMemoryResourceRepository).dropAll()
      }

      test("adds tag to sg ") {
        checkResources()
        verify { resourcePersister.create(any()) }
      }
    }

    context("cluster created") {
      before {
        onApplicationUp()
        resourceRepository.store(rCluster)
      }

      after {
        onApplicationDown()
        (resourceRepository as InMemoryResourceRepository).dropAll()
      }
    }

    context("cluster deleted") {
      before {
        onApplicationUp()

        resourceRepository.store(rCluster)
        resourceRepository.store(rClusterTag)
      }

      every {
        resourcePersister.update(clusterTagName, any())
      } answers { Resource(arg(1), ResourceMetadata(clusterTagName, randomUID())) }

      after {
        onApplicationDown()
        (resourceRepository as InMemoryResourceRepository).dropAll()
      }

      test("removes cluster tag on delete") {
        onDeleteEvent(DeleteEvent(clusterName))

        verify { resourcePersister.update(clusterTagName, any()) }
      }
    }
  }
}
