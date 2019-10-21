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
package com.netflix.spinnaker.keel.diff

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.diff.DiffStatus.DIFF
import com.netflix.spinnaker.keel.diff.DiffStatus.ERROR
import com.netflix.spinnaker.keel.diff.DiffStatus.MISSING
import com.netflix.spinnaker.keel.diff.DiffStatus.NO_DIFF
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component

@Component
class AdHocDiffer(
  private val handlers: List<ResourceHandler<*, *>>
) {
  fun <T : ResourceSpec> calculate(submittedResource: SubmittedResource<T>): DiffResult =
    runBlocking {
      var resourceId: ResourceId? = null
      var resource: Resource<T>? = null
      try {
        val plugin = handlerFor(submittedResource)
        resource = plugin.normalize(submittedResource)
        resourceId = resource.id
        val (desired, current) = plugin.resolve(resource)
        val diff = ResourceDiff(desired, current)

        when {
          current == null -> DiffResult(status = MISSING, resourceId = resourceId, resource = resource)
          diff.hasChanges() -> DiffResult(status = DIFF, diff = diff.toDeltaJson(), resourceId = resourceId, resource = resource)
          else -> DiffResult(status = NO_DIFF, resourceId = resourceId, resource = resource)
        }
      } catch (e: Exception) {
        DiffResult(status = ERROR, errorMsg = e.message, resourceId = resourceId, resource = resource)
      }
    }

  fun calculate(submittedDeliveryConfig: SubmittedDeliveryConfig): List<EnvironmentDiff> =
    runBlocking {
      submittedDeliveryConfig.environments.map { env ->
        val resourceDiffs = env.resources.map { resource ->
          async { calculate(resource) }
        }.awaitAll()

        EnvironmentDiff(
          name = env.name,
          manifestName = submittedDeliveryConfig.name,
          resourceDiffs = resourceDiffs
        )
      }
    }

  @Suppress("UNCHECKED_CAST")
  private fun <T : ResourceSpec> handlerFor(resource: SubmittedResource<T>) =
    handlers.supporting(
      resource.apiVersion,
      resource.kind
    ) as ResourceHandler<T, *>

  private suspend fun ResourceHandler<*, *>.resolve(resource: Resource<out ResourceSpec>): Pair<Any, Any?> =
    coroutineScope {
      val desired = async {
        try {
          desired(resource)
        } catch (e: Throwable) {
          throw CannotResolveDesiredState(resource.id, e)
        }
      }
      val current = async {
        try {
          current(resource)
        } catch (e: Throwable) {
          throw CannotResolveCurrentState(resource.id, e)
        }
      }
      desired.await() to current.await()
    }

  // These extensions get round the fact tht we don't know the spec type of the resource from
  // the repository. I don't want the `ResourceHandler` interface to be untyped though.
  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.desired(
    resource: Resource<*>
  ): R =
    desired(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.current(
    resource: Resource<*>
  ): R? =
    current(resource as Resource<S>)
  // end type coercing extensions
}
