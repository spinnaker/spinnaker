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
package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.pause.Pause
import com.netflix.spinnaker.keel.pause.PauseScope
import com.netflix.spinnaker.keel.pause.PauseScope.APPLICATION
import com.netflix.spinnaker.keel.pause.PauseScope.RESOURCE
import com.netflix.spinnaker.keel.persistence.PausedRepository
import java.time.Clock

class InMemoryPausedRepository(override val clock: Clock = Clock.systemUTC()) : PausedRepository {
  private val paused: MutableList<Pause> = mutableListOf()

  override fun getPause(scope: PauseScope, name: String): Pause? {
    return paused.find { it.scope == scope && it.name == name }
  }

  override fun pauseApplication(application: String, user: String) {
    paused.add(Pause(APPLICATION, application, user, clock.instant()))
  }

  override fun resumeApplication(application: String) {
    paused.removeIf { it.scope == APPLICATION && it.name == application }
  }

  override fun pauseResource(id: String, user: String) {
    paused.add(Pause(RESOURCE, id, user, clock.instant()))
  }

  override fun resumeResource(id: String) {
    paused.removeIf { it.scope == RESOURCE && it.name == id }
  }

  override fun resourcePaused(id: String): Boolean =
    paused.any { it.scope == RESOURCE && it.name == id }

  override fun getPausedResources(): List<String> =
    paused.filter { it.scope == RESOURCE }.map { it.name }.toList()

  override fun applicationPaused(application: String): Boolean =
    paused.any { it.scope == APPLICATION && it.name == application }

  override fun getPausedApplications(): List<String> =
    paused.filter { it.scope == APPLICATION }.map { it.name }.toList()

  fun flush() =
    paused.clear()
}
