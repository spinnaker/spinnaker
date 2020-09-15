/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.schema.Description
import com.netflix.spinnaker.keel.api.generateId
import com.netflix.spinnaker.keel.api.schema.Discriminator

/**
 * External representation of a resource that would be submitted to the API
 */
@Description("A resource as submitted to the Managed Delivery API.")
data class SubmittedResource<T : ResourceSpec>(
  @Description("Optional metadata about the resource.")
  val metadata: Map<String, Any?> = emptyMap(),

  @Discriminator
  @Description("The kind of resource `spec` represents.")
  val kind: ResourceKind,

  @Description("The specification of the resource")
  @JsonTypeInfo(use = Id.NAME, include = As.EXTERNAL_PROPERTY, property = "kind")
  val spec: T
)

val <T : ResourceSpec> SubmittedResource<T>.id: String
  get() = generateId(kind, spec)

fun <T : ResourceSpec> SubmittedResource<T>.normalize(): Resource<T> =
  Resource(
    kind = kind,
    metadata = metadata + mapOf(
      "id" to id,
      "uid" to randomUID().toString(),
      "application" to spec.application
    ),
    spec = spec
  )
