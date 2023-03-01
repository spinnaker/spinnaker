/*
 * Copyright 2023 JPMorgan Chase & Co.
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

package com.netflix.spinnaker.front50.migrations

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.front50.model.ObjectType
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.web.context.RequestContextProvider
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.*
import java.util.concurrent.TimeUnit

class StorageServiceMigratorTests : JUnit5Minutests {
  private val dynamicConfigService: DynamicConfigService = mockk(relaxUnitFun = true)
  private val target: SqlStorageService = mockk(relaxUnitFun = true)
  private val source: SqlStorageService = mockk(relaxUnitFun = true)
  private val entityTagsDAO: EntityTagsDAO = mockk(relaxUnitFun = true)
  private val contextProvider: RequestContextProvider = mockk(relaxUnitFun = true)

  private val subject = StorageServiceMigrator(dynamicConfigService, NoopRegistry(), target, source, entityTagsDAO, contextProvider)

  fun tests() = rootContext {
    after {
      clearMocks(dynamicConfigService, target, source, entityTagsDAO, contextProvider)
    }

    context("migrate()") {
      test("should not delete orphaned objects less than 5 minutes old") {
        every {
          source.listObjectKeys(ObjectType.APPLICATION)
        } returns mapOf()

        every {
          target.listObjectKeys(ObjectType.APPLICATION)
        } returns mapOf("id-application-0" to System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3))

        every {
          dynamicConfigService.getConfig(
            Boolean::class.java,
            "spinnaker.migration.compositeStorageService.deleteOrphans",
            any()
          )
        } returns true

        subject.migrate(ObjectType.APPLICATION)

        verify(exactly = 0) {
          target.deleteObject(ObjectType.APPLICATION, "id-application-0")
        }
      }

      test("should delete orphaned objects in primary when deleteOrphans is true") {
        every {
          source.listObjectKeys(ObjectType.APPLICATION)
        } returns mapOf()

        every {
          target.listObjectKeys(ObjectType.APPLICATION)
        } returns mapOf("id-application-0" to System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(6))

        every {
          dynamicConfigService.getConfig(
            Boolean::class.java,
            "spinnaker.migration.compositeStorageService.deleteOrphans",
            any()
          )
        } returns true

        subject.migrate(ObjectType.APPLICATION)

        verify(exactly = 1) {
          target.deleteObject(ObjectType.APPLICATION, "id-application-0")
        }
      }

      test("should not delete orphaned objects in primary when deleteOrphans is false") {
        every {
          source.listObjectKeys(ObjectType.APPLICATION)
        } returns mapOf()

        every {
          target.listObjectKeys(ObjectType.APPLICATION)
        } returns mapOf("id-application-0" to System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(6))

        every {
          dynamicConfigService.getConfig(
            Boolean::class.java,
            "spinnaker.migration.compositeStorageService.deleteOrphans",
            any()
          )
        } returns false

        subject.migrate(ObjectType.APPLICATION)

        verify(exactly = 0) {
          target.deleteObject(ObjectType.APPLICATION, "id-application-0")
        }
      }
    }
  }
}
