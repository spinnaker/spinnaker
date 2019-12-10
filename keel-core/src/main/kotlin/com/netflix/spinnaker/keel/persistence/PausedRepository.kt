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
package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ResourceId

/**
 * A repository to track what scopes are paused, starting with application
 */
interface PausedRepository {

  fun pauseApplication(application: String)
  fun resumeApplication(application: String)
  fun applicationPaused(application: String): Boolean

  fun pauseResource(id: ResourceId)
  fun resumeResource(id: ResourceId)
  fun resourcePaused(id: ResourceId): Boolean

  fun getPausedApplications(): List<String>
  fun getPausedResources(): List<ResourceId>

  // todo eb: add environment
  enum class Scope {
    APPLICATION, RESOURCE;
  }
}
