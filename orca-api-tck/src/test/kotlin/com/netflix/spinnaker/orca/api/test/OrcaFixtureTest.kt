/*
 * Copyright 2020 Netflix, Inc.
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
 */
package com.netflix.spinnaker.orca.api.test

import com.netflix.spinnaker.orca.notifications.AlwaysUnlockedNotificationClusterLock
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.InMemoryExecutionRepository
import com.netflix.spinnaker.orca.q.pending.InMemoryPendingExecutionService
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.memory.InMemoryQueue
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.beans.factory.annotation.Autowired
import strikt.api.expect
import strikt.assertions.isA

class OrcaFixtureTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    context("an orca integration test environment") {
      orcaFixture {
        Fixture()
      }

      test("the application starts with expected in-memory beans") {
        expect {
          that(executionRepository).isA<InMemoryExecutionRepository>()
          that(queue).isA<InMemoryQueue>()
          that(notificationClusterLock).isA<AlwaysUnlockedNotificationClusterLock>()
          that(pendingExecutionService).isA<InMemoryPendingExecutionService>()
        }
      }
    }
  }

  private inner class Fixture : OrcaFixture() {

    @Autowired
    lateinit var executionRepository: ExecutionRepository

    @Autowired
    lateinit var queue: Queue

    @Autowired
    lateinit var notificationClusterLock: NotificationClusterLock

    @Autowired
    lateinit var pendingExecutionService: PendingExecutionService
  }
}
