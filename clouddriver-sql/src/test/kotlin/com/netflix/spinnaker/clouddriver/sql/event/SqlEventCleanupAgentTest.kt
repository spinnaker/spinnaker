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
 *
 */
package com.netflix.spinnaker.clouddriver.sql.event

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.SqlEventCleanupAgentConfigProperties
import com.netflix.spinnaker.config.SqlEventCleanupAgentConfigProperties.Companion.EVENT_CLEANUP_LIMIT
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.jooq.impl.DSL
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

class SqlEventCleanupAgentTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {
      listOf(
        Instant.now().minus(3, ChronoUnit.DAYS),
        Instant.now().minus(10, ChronoUnit.DAYS)
      ).forEachIndexed { i, ts ->
        database.context
          .insertInto(DSL.table("event_aggregates"))
          .values(
            "mytype",
            "myid$i",
            ULID().nextULID(),
            1,
            Timestamp.from(ts)
          )
          .execute()
      }
    }

    after {
      SqlTestUtil.cleanupDb(database.context)
    }

    test("deletes old aggregates") {
      subject.run()

      val count = database.context.fetchCount(DSL.table("event_aggregates"))
      expectThat(count).isEqualTo(1)
    }
  }

  private inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!
    val dynamicConfigService: DynamicConfigService = mockk(relaxed = true)

    val subject = SqlEventCleanupAgent(
      database.context,
      NoopRegistry(),
      SqlEventCleanupAgentConfigProperties(),
      dynamicConfigService
    )

    init {
      every { dynamicConfigService.getConfig(eq(Int::class.java), any(), eq(EVENT_CLEANUP_LIMIT)) } returns EVENT_CLEANUP_LIMIT
    }
  }
}
