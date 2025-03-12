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
package com.netflix.spinnaker.keel.tags

/**
 * The representation of an entity tag.
 * The value can be any object.
 */
data class EntityTag(
  val value: Any,
  val namespace: String,
  val valueType: String,
  val name: String
)

/**
 * The value of a keel entity tag.
 * Entity tags can be of any value, but for keel tags we want
 * an object of this form.
 */
data class TagValue(
  val message: String,
  val keelResourceId: String,
  val type: String
)

/**
 * Represents the id of an entity as used by the entity tags api
 */
data class EntityRef(
  val entityType: String,
  val entityId: String,
  val application: String,
  val region: String,
  val account: String, // account name
  val accountId: String, // account number
  val cloudProvider: String
) {
  fun generateId(): String {
    return "$cloudProvider:$entityType:$entityId:$accountId:$region"
  }
}

/**
 * A response containing all entity tags for an object
 */
data class EntityTags(
  val id: String,
  val idPattern: String,
  val tags: List<EntityTag>,
  val tagsMetadata: List<TagsMetadata>,
  val entityRef: EntityRef
)

/**
 * Metadata about entity tags for an object.
 * Used only in the [EntityTags] response
 */
data class TagsMetadata(
  val name: String,
  val lastModified: Long,
  val lastModifiedBy: String,
  val created: Long,
  val createdBy: String
)

const val KEEL_TAG_NAME = "spinnaker_ui_notice:managed_by_spinnaker"
