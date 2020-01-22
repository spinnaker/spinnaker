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
package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.netflix.spinnaker.keel.serialization.SubmittedResourceDeserializer

/**
 * Internal representation of a resource.
 */
data class Resource<T : ResourceSpec>(
  val apiVersion: ApiVersion,
  val kind: String, // TODO: create a type
  val metadata: Map<String, Any?>,
  val spec: T
) {
  init {
    require(kind.isNotEmpty()) { "resource kind must be defined" }
    require(metadata["id"].isValidId()) { "resource id must be a valid id" }
    require(metadata["serviceAccount"].isValidServiceAccount()) { "serviceAccount must be a valid service account" }
    require(metadata["application"].isValidApplication()) { "application must be a valid application" }
  }

  constructor(resource: SubmittedResource<T>, metadata: Map<String, Any?>) :
    this(resource.apiVersion, resource.kind, metadata, resource.spec)

  // TODO: this is kinda dirty, but because we add uid to the metadata when persisting we don't really want to consider it in equality checks
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Resource<*>

    if (apiVersion != other.apiVersion) return false
    if (kind != other.kind) return false
    if (spec != other.spec) return false

    return true
  }

  override fun hashCode(): Int {
    var result = apiVersion.hashCode()
    result = 31 * result + kind.hashCode()
    result = 31 * result + spec.hashCode()
    return result
  }
}

/**
 * External representation of a resource that would be submitted to the API
 */
@JsonDeserialize(using = SubmittedResourceDeserializer::class)
data class SubmittedResource<T : ResourceSpec>(
  val metadata: Map<String, Any?> = emptyMap(),
  val apiVersion: ApiVersion,
  val kind: String,
  val spec: T
)

val <T : ResourceSpec> SubmittedResource<T>.id: ResourceId
  get() = "${apiVersion.prefix}:$kind:${spec.id}".let(::ResourceId)

val <T : ResourceSpec> Resource<T>.id: ResourceId
  get() = metadata.getValue("id").toString().let(::ResourceId)

val <T : ResourceSpec> Resource<T>.serviceAccount: String
  get() = metadata.getValue("serviceAccount").toString()

val <T : ResourceSpec> Resource<T>.application: String
  get() = metadata.getValue("application").toString()

private fun Any?.isValidId() =
  when (this) {
    is String -> isNotBlank()
    else -> false
  }

private fun Any?.isValidServiceAccount() =
  when (this) {
    is String -> isNotBlank()
    else -> false
  }

private fun Any?.isValidApplication() =
  when (this) {
    is String -> isNotBlank()
    else -> false
  }
