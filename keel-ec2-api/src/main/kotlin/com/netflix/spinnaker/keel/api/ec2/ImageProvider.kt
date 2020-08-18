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
package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

/**
 * Base interface for providing an image
 */
sealed class ImageProvider

/**
 * Provides image id by reference to a package
 */
@Deprecated(
  "Non-reference-based artifact providers are no longer supported.",
  replaceWith = ReplaceWith("ReferenceArtifactImageProvider")
)
data class ArtifactImageProvider(
  val deliveryArtifact: DeliveryArtifact,
  val artifactStatuses: List<ArtifactStatus> = emptyList() // treated as "all statuses" by ImageResolver
) : ImageProvider()

/**
 * Provides image id by referencing an artifact defined in the delivery config
 */
data class ReferenceArtifactImageProvider(
  val reference: String
) : ImageProvider()

/**
 * Provides an image by reference to a jenkins master, job, and job number
 */
@Deprecated(
  "Non-reference-based artifact providers are no longer supported.",
  replaceWith = ReplaceWith("ReferenceArtifactImageProvider")
)
data class JenkinsImageProvider(
  val packageName: String,
  val buildHost: String,
  val buildName: String,
  val buildNumber: String
) : ImageProvider()
