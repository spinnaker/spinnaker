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

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import de.huxhorn.sulky.ulid.ULID

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
    require(metadata["uid"].isValidULID()) { "resource uid must be a valid ULID" }
    require(metadata["id"].isValidId()) { "resource id must be a valid id" }
    require(metadata["serviceAccount"].isValidServiceAccount()) { "serviceAccount must be a valid service account" }
    require(metadata["application"].isValidApplication()) { "application must be a valid application" }
  }

  constructor(resource: SubmittedResource<T>, metadata: Map<String, Any?>) :
    this(resource.apiVersion, resource.kind, metadata, resource.spec)
}

/**
 * External representation of a resource that would be submitted to the API
 */
data class SubmittedResource<T : ResourceSpec>(
  val metadata: SubmittedMetadata,
  val apiVersion: ApiVersion,
  val kind: String,
  @JsonTypeInfo(
    use = Id.NAME,
    include = As.EXTERNAL_PROPERTY,
    property = "kind"
  )
  val spec: T
)

val <T : ResourceSpec> SubmittedResource<T>.id: ResourceId
  get() = "${apiVersion.prefix}:$kind:${spec.id}".let(::ResourceId)

/**
 * Required metadata to be submitted with a resource
 */
data class SubmittedMetadata(
  val serviceAccount: String
)

val <T : ResourceSpec> Resource<T>.uid: UID
  get() = metadata.getValue("uid").toString().let(ULID::parseULID)

val <T : ResourceSpec> Resource<T>.id: ResourceId
  get() = metadata.getValue("id").toString().let(::ResourceId)

val <T : ResourceSpec> Resource<T>.serviceAccount: String
  get() = metadata.getValue("serviceAccount").toString()

val <T : ResourceSpec> Resource<T>.application: String
  get() = metadata.getValue("application").toString()

private fun Any?.isValidULID() =
  when (this) {
    is UID -> true
    is String -> runCatching {
      ULID.parseULID(toString())
    }.isSuccess
    else -> false
  }

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
