/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.jooq.SQLDialect
import org.junit.jupiter.api.AfterAll
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import java.time.Clock

internal object SqlStorageServiceTests : JUnit5Minutests {
  private val jooq = initDatabase(
    "jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    SQLDialect.MYSQL_5_7
  )

  private val sqlStorageService = SqlStorageService(
    ObjectMapper(),
    NoopRegistry(),
    jooq,
    Clock.systemDefaultZone(),
    SqlRetryProperties()
  )

  fun tests() = rootContext {
    after {
      jooq.flushAll()
    }

    context("Application CRUD") {
      test("throws NotFoundException when application does not exist") {
        expectThrows<NotFoundException> {
          sqlStorageService.loadObject<Application>(ObjectType.APPLICATION, "application001")
        }
      }

      test("creates an application") {
        sqlStorageService.storeObject(
          ObjectType.APPLICATION,
          "application001",
          Application().apply {
            name = "application001"
            description = "my first application!"
            updateTs = "100"
          }
        )

        val application = sqlStorageService.loadObject<Application>(ObjectType.APPLICATION, "application001")
        expectThat(application.description).isEqualTo("my first application!")
      }

      test("deletes an application") {
        sqlStorageService.deleteObject(ObjectType.APPLICATION, "application001")

        expectThrows<NotFoundException> {
          sqlStorageService.loadObject<Application>(ObjectType.APPLICATION, "application001")
        }
      }
    }
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    jooq.close()
  }
}

