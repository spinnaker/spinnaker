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
package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.persistence.ResourceStatus

/**
 * A summary version of a resource that contains identifying information, location information, and status.
 * This powers the UI view of resource status.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder(value = ["id", "kind", "status", "moniker", "locations", "artifact"])
data class ResourceSummary(
  @JsonIgnore
  val resource: Resource<*>,
  val status: ResourceStatus,
  val moniker: Moniker?,
  val locations: Locations<*>?,
  val artifact: ResourceArtifactSummary? = null
) {
  val id: String = resource.id
  val kind: ResourceKind = resource.kind
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceArtifactSummary(
  val name: String?,
  val type: ArtifactType?,
  val reference: String?
)
