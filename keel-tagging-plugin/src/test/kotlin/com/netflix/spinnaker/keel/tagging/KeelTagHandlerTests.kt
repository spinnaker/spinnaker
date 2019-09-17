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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.EntityTags
import com.netflix.spinnaker.keel.tags.KEEL_TAG_NAME
import com.netflix.spinnaker.keel.tags.TagsMetadata
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.util.UUID

val RETROFIT_NOT_FOUND = HttpException(
  Response.error<Any>(404, ResponseBody.create(MediaType.parse("application/json"), ""))
)

internal class KeelTagHandlerTests : JUnit5Minutests {

  val clock = MutableClock()

  val keelId = ResourceId("ec2:server-group:test:us-west-1:emburnstest-managed-reference")
  val entityRef = EntityRef(
    entityType = "servergroup",
    entityId = "emburnstest-managed-reference-v005",
    application = "emburnstest",
    region = "us-west-1",
    account = "test",
    accountId = "1234",
    cloudProvider = "aws"
  )

  val managedByKeelTag = EntityTag(
    value = mapOf(
      "message" to KEEL_TAG_MESSAGE,
      "keelResourceId" to keelId,
      "type" to "notice"
    ),
    namespace = KEEL_TAG_NAMESPACE,
    valueType = "object",
    name = KEEL_TAG_NAME
  )

  val specWithTag = KeelTagSpec(
    keelId = keelId,
    entityRef = entityRef,
    tagState = TagDesired(
      tag = managedByKeelTag
    )
  )

  val specWithoutTag = specWithTag.copy(tagState = TagNotDesired(clock.millis()))

  val taggedResourceWithKeelTag = TaggedResource(
    keelId = keelId,
    entityRef = entityRef,
    relevantTag = managedByKeelTag
  )

  val taggedResourceWithoutTag = taggedResourceWithKeelTag.copy(relevantTag = null)

  val resourceWithTag = resource(
    kind = "keel-tag",
    spec = specWithTag
  )

  val resourceWithoutTag = resourceWithTag.copy(spec = specWithoutTag)

  val keelTagsMetadata = TagsMetadata(
    name = KEEL_TAG_NAME,
    lastModified = System.currentTimeMillis(),
    lastModifiedBy = "keel",
    created = System.currentTimeMillis(),
    createdBy = "keel"
  )

  val entityTags = EntityTags(
    id = entityRef.generateId(),
    idPattern = "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}",
    tags = listOf(managedByKeelTag),
    tagsMetadata = listOf(keelTagsMetadata),
    entityRef = entityRef
  )

  val cloudDriverService = mockk<CloudDriverService>()
  val orcaService = mockk<OrcaService>()
  val objectMapper = ObjectMapper().registerKotlinModule()
  val normalizers = emptyList<ResourceNormalizer<KeelTagSpec>>()

  fun tests() = rootContext<KeelTagHandler> {
    fixture {
      KeelTagHandler(
        cloudDriverService,
        orcaService,
        objectMapper,
        normalizers
      )
    }

    before {
      coEvery { orcaService.orchestrate("keel@spinnaker", any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
    }

    after {
      confirmVerified(orcaService)
    }

    context("server group doesn't have tags") {
      before {
        coEvery { cloudDriverService.getTagsForEntity(entityRef.generateId()) } throws RETROFIT_NOT_FOUND
      }

      context("tag desired") {
        test("current is resolved correctly") {
          val current = runBlocking { current(resourceWithTag) }
          val desired = runBlocking { desired(resourceWithTag) }
          val diff = ResourceDiff(desired, current)
          expect {
            that(current).isNotNull().isA<TaggedResource>().get { relevantTag }.isNull()
            that(diff).get { hasChanges() }.isEqualTo(true)
          }
        }

        test("tag gets upserted") {
          val current = runBlocking { current(resourceWithTag) }
          val desired = runBlocking { desired(resourceWithTag) }
          val diff = ResourceDiff(desired, current)
          runBlocking { upsert(resourceWithTag, diff) }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("upsertEntityTags")
          }
        }
      }

      test("we don't want a tag") {
        val current = runBlocking { current(resourceWithoutTag) }
        val desired = runBlocking { desired(resourceWithoutTag) }
        val diff = ResourceDiff(desired, current)
        expect {
          that(current).isNotNull().isA<TaggedResource>().get { relevantTag }.isNull()
          that(diff).get { hasChanges() }.isEqualTo(false)
        }
      }
    }

    context("server group has an entity tag") {
      before {
        coEvery { cloudDriverService.getTagsForEntity(entityRef.generateId()) } returns entityTags
      }

      test("we want a tag") {
        val current = runBlocking { current(resourceWithTag) }
        val desired = runBlocking { desired(resourceWithTag) }
        val diff = ResourceDiff(desired, current)
        expect {
          that(current).isNotNull().isA<TaggedResource>().get { relevantTag }.isEqualTo(managedByKeelTag)
          that(diff).get { hasChanges() }.isEqualTo(false)
        }
      }

      test("we don't want a tag") {
        val current = runBlocking { current(resourceWithoutTag) }
        val desired = runBlocking { desired(resourceWithoutTag) }
        val diff = ResourceDiff(desired, current)
        expect {
          that(current).isNotNull().isA<TaggedResource>().get { relevantTag }.isEqualTo(managedByKeelTag)
          that(diff).get { hasChanges() }.isEqualTo(true)
        }
      }

      test("we don't want a tag and the tag gets removed") {
        val current = runBlocking { current(resourceWithoutTag) }
        val desired = runBlocking { desired(resourceWithoutTag) }
        val diff = ResourceDiff(desired, current)
        runBlocking { upsert(resourceWithTag, diff) }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }
        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("deleteEntityTags")
        }
      }
    }
  }
}
