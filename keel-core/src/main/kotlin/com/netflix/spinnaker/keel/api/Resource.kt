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

import de.huxhorn.sulky.ulid.ULID

/**
 * Internal representation of a resource.
 */
data class Resource<T : Any>(
  val apiVersion: ApiVersion,
  val kind: String, // TODO: create a type
  val metadata: Map<String, Any?>,
  val spec: T
) {
  init {
    require(kind.isNotEmpty()) { "resource kind must be defined" }
    require(metadata["uid"].isValidULID()) { "resource uid must be a valid ULID" }
    require(metadata["name"].isValidName()) { "resource name must be a valid name" }
  }

  constructor(resource: SubmittedResource<T>, metadata: Map<String, Any?>) :
    this(resource.apiVersion, resource.kind, metadata, resource.spec)
}

/**
 * External representation of a resource that would be submitted to the API
 * It doesn't need to contain metadata
 */
data class SubmittedResource<T : Any>(
  val apiVersion: ApiVersion,
  val kind: String,
  val spec: T
)

val <T : Any> Resource<T>.uid: UID
  get() = metadata.getValue("uid").toString().let(ULID::parseULID)

val <T : Any> Resource<T>.name: ResourceName
  get() = metadata.getValue("name").toString().let(::ResourceName)

private fun Any?.isValidULID() =
  when (this) {
    is UID -> true
    is String -> runCatching {
      ULID.parseULID(toString())
    }.isSuccess
    else -> false
  }

private fun Any?.isValidName() =
  when (this) {
    is String -> isNotBlank()
    else -> false
  }
