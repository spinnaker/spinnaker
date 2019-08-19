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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.netflix.spinnaker.keel.api.HasApplication
import com.netflix.spinnaker.keel.api.Named
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag

/**
 * The desired for Spinnaker tagging of resources we manage
 *
 * If [keelTagged] is true the resource should only have tag
 * in the [KEEL_TAG_NAMESPACE], and that tag should match the given tag.
 *
 * If [keelTagged] is false the resource should have no tags in
 * the [KEEL_TAG_NAMESPACE].
 */
data class KeelTagSpec(
  val keelId: String,
  val entityRef: EntityRef,
  val tagState: TagState
) : Named, HasApplication {
  @JsonIgnore
  override val name: String = keelId

  @JsonIgnore
  override val application: String = entityRef.application
}

data class TaggedResource(
  val keelId: String,
  val entityRef: EntityRef,
  val relevantTag: EntityTag?
)

@JsonDeserialize(using = TagStateDeserializer::class)
sealed class TagState

@JsonDeserialize(using = JsonDeserializer.None::class)
data class TagDesired(
  val tag: EntityTag
) : TagState()

/**
 * Desire no keel tags
 */
@JsonDeserialize(using = JsonDeserializer.None::class)
data class TagNotDesired(
  val startTime: Long
) : TagState()

const val KEEL_TAG_NAMESPACE = "keel"
const val KEEL_TAG_MESSAGE = "Managed Declaratively"
