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
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.retrofit.isNotFound
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.EntityTags
import com.netflix.spinnaker.keel.tags.KEEL_TAG_NAME
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.HttpException

/**
 * Ensures that keel resources are tagged with entity tags indicating
 * management by keel
 */
class KeelTagHandler(
  private val cloudDriverService: CloudDriverService,
  private val orcaService: OrcaService,
  override val objectMapper: ObjectMapper,
  override val resolvers: List<Resolver<*>>
) : ResourceHandler<KeelTagSpec, TaggedResource> {

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("tag")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "keel-tag",
    "keel-tags"
  ) to KeelTagSpec::class.java

  override suspend fun desired(resource: Resource<KeelTagSpec>): TaggedResource {
    when (resource.spec.tagState) {
      is TagDesired -> {
        val desiredTag = (resource.spec.tagState as TagDesired).tag
        return TaggedResource(resource.spec.keelId, resource.spec.entityRef, desiredTag)
      }
      is TagNotDesired -> {
        return TaggedResource(resource.spec.keelId, resource.spec.entityRef, null)
      }
    }
  }

  override suspend fun current(resource: Resource<KeelTagSpec>): TaggedResource? {
    val entityTags = getEntityTags(resource)

    when (resource.spec.tagState) {
      is TagDesired -> {
        val desiredTag = (resource.spec.tagState as TagDesired).tag
        val desired = TaggedResource(resource.spec.keelId, resource.spec.entityRef, desiredTag)
        return if (entityTags.containsTag(desiredTag)) {
          desired
        } else {
          TaggedResource(resource.spec.keelId, resource.spec.entityRef, null)
        }
      }
      is TagNotDesired -> {
        return if (entityTags.containsKeelTag()) {
          TaggedResource(
            resource.spec.keelId,
            resource.spec.entityRef,
            entityTags.tags.find { it.namespace == KEEL_TAG_NAMESPACE && it.name == KEEL_TAG_NAME }
          )
        } else {
          TaggedResource(resource.spec.keelId, resource.spec.entityRef, null)
        }
      }
    }
  }

  override suspend fun upsert(
    resource: Resource<KeelTagSpec>,
    resourceDiff: ResourceDiff<TaggedResource>
  ): List<Task> {
    val desired = resourceDiff.desired
    val current = resourceDiff.current

    val job = when {
      resourceDiff.needsTag() -> desired.createTagJob()
      current != null -> current.removeTagJob()
      else -> desired.removeTagJob()
    }

    val description = "Upsert entity tag for resource ${resource.spec.keelId}"
    val taskResponse = orcaService.orchestrate(
      resource.serviceAccount,
      OrchestrationRequest(
        description,
        desired.entityRef.application,
        description,
        listOf(Job(job["type"].toString(), job)),
        OrchestrationTrigger(correlationId = resource.id.toString(), notifications = emptyList())
      ))
    log.info("Started task {} to upsert entity tags", taskResponse.ref)
    return listOf(Task(id = taskResponse.taskId, name = description))
  }

  private fun ResourceDiff<TaggedResource>.needsTag(): Boolean {
    return desired.relevantTag != null && current?.relevantTag == null
  }

  private suspend fun getEntityTags(resource: Resource<KeelTagSpec>): EntityTags {
    try {
      return cloudDriverService.getTagsForEntity(resource.spec.entityRef.generateId())
    } catch (e: HttpException) {
      if (e.isNotFound) {
        // a 404 is thrown when there are no tags found
        return EntityTags(
          id = resource.spec.entityRef.generateId(),
          idPattern = "",
          tags = emptyList(),
          tagsMetadata = emptyList(),
          entityRef = resource.spec.entityRef
        )
      } else {
        log.error("Error fetching tags for ${resource.spec.keelId}: ", e)
        throw e
      }
    }
  }

  override suspend fun delete(resource: Resource<KeelTagSpec>) {
    TODO("not implemented")
  }

  private fun EntityTags.containsTag(requestedTag: EntityTag): Boolean = tags
    .filter { it.namespace == KEEL_TAG_NAMESPACE }
    .any { it.name == requestedTag.name }

  private fun EntityTags.containsKeelTag(): Boolean =
    tags.any { it.namespace == KEEL_TAG_NAMESPACE && it.name == KEEL_TAG_NAME }

  /**
   * Orca Job for removing Keel tags
   */
  private fun TaggedResource.removeTagJob() =
    mapOf(
      "type" to "deleteEntityTags",
      "application" to entityRef.application,
      "description" to "Removing entity tag for $keelId",
      "tags" to listOf(relevantTag?.name),
      "id" to entityRef.generateId()
    )

  /**
   * Orca job for tagging with the standard [KEEL_TAG_NAME] tag
   */
  private fun TaggedResource.createTagJob() =
    mutableMapOf(
      "type" to "upsertEntityTags",
      "application" to entityRef.application,
      "description" to "Add keel tag to $keelId",
      "entityRef" to entityRef,
      "tags" to listOf(relevantTag)
    )
}

const val KEEL_TAG_ID_PREFIX = "tag:keel-tag"
