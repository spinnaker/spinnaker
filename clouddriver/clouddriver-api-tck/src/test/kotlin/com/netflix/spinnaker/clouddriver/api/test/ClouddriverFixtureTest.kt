/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.api.test

import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.data.task.InMemoryTaskRepository
import com.netflix.spinnaker.clouddriver.event.persistence.InMemoryEventRepository
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.beans.factory.annotation.Autowired
import strikt.api.expect
import strikt.assertions.isA

class ClouddriverFixtureTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    context("a clouddriver integration test environment") {
      clouddriverFixture {
        Fixture()
      }

      test("the application starts with expected in-memory beans") {
        expect {
          that(taskRepository).isA<InMemoryTaskRepository>()
          that(eventRepository).isA<InMemoryEventRepository>()
          that(namedCacheFactory).isA<InMemoryNamedCacheFactory>()
        }
      }
    }
  }

  inner class Fixture : ClouddriverFixture() {

    @Autowired
    lateinit var taskRepository: TaskRepository

    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var namedCacheFactory: NamedCacheFactory
  }
}
