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
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.tag.EntityTags
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import dev.minutest.ContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

internal object SqlStorageServiceTests : JUnit5Minutests {

  class JooqConfig(val dialect: SQLDialect, val jdbcUrl: String)

  fun ContextBuilder<JooqConfig>.crudOperations(jooqConfig: JooqConfig) {

    val jooq = initDatabase(
      jooqConfig.jdbcUrl,
      jooqConfig.dialect
    )
    val sqlStorageService = SqlStorageService(
      ObjectMapper(),
      NoopRegistry(),
      jooq,
      Clock.systemDefaultZone(),
      SqlRetryProperties(),
      1,
      "default"
    )
    context("For ${jooqConfig.dialect}") {
      context("Application") {
        test("throws NotFoundException when application does not exist") {
          expectThrows<NotFoundException> {
            sqlStorageService.loadObject<Application>(ObjectType.APPLICATION, "application001")
          }
        }

        test("create, update and delete an application") {
          // verify that an application can be created
          sqlStorageService.storeObject(
            ObjectType.APPLICATION,
            "application001",
            Application().apply {
              name = "application001"
              description = "my first application!"
              lastModified = 100
            }
          )

          var application = sqlStorageService.loadObject<Application>(ObjectType.APPLICATION, "application001")
          expectThat(application.description).isEqualTo("my first application!")

          // verify that an application can be updated
          sqlStorageService.storeObject(
            ObjectType.APPLICATION,
            "application001",
            Application().apply {
              name = "application001"
              description = "my updated application!"
              lastModified = 200
            }
          )

          application = sqlStorageService.loadObject(ObjectType.APPLICATION, "application001")
          expectThat(application.description).isEqualTo("my updated application!")

          // verify that history can be retrieved
          val applicationVersions = sqlStorageService.listObjectVersions<Application>(
            ObjectType.APPLICATION, "application001", 5
          )
          expectThat(applicationVersions.size).isEqualTo(2)
          expectThat(applicationVersions.get(0).description).isEqualTo("my updated application!")
          expectThat(applicationVersions.get(1).description).isEqualTo("my first application!")

          // delete the application
          sqlStorageService.deleteObject(ObjectType.APPLICATION, "application001")

          expectThrows<NotFoundException> {
            sqlStorageService.loadObject<Application>(ObjectType.APPLICATION, "application001")
          }

          // recover the application
          sqlStorageService.recover(
            AdminOperations.Recover().apply {
              objectType = "application"
              objectId = "application001"
            }
          )

          application = sqlStorageService.loadObject(ObjectType.APPLICATION, "application001")
          expectThat(application.description).isEqualTo("my updated application!")
        }
      }

      context("Pipeline") {
        test("create, update and delete a pipeline") {
          // verify that a pipeline can be created
          sqlStorageService.storeObject(
            ObjectType.PIPELINE,
            "id-pipeline001",
            Pipeline().apply {
              name = "pipeline001"
              lastModified = 100

              put("application", "application001")
            }
          )

          var pipeline = sqlStorageService.loadObject<Pipeline>(ObjectType.PIPELINE, "id-pipeline001")
          expectThat(pipeline.name).isEqualTo("pipeline001")

          // verify that a pipeline can be updated
          sqlStorageService.storeObject(
            ObjectType.PIPELINE,
            "id-pipeline001",
            Pipeline().apply {
              name = "pipeline001_updated"
              lastModified = 200

              put("application", "application001")
            }
          )

          pipeline = sqlStorageService.loadObject(ObjectType.PIPELINE, "id-pipeline001")
          expectThat(pipeline.name).isEqualTo("pipeline001_updated")

          expectThat(
            jooq
              .select(DSL.field("id", String::class.java))
              .from("pipelines")
              .where(
                DSL.field("name", String::class.java).eq("pipeline001_updated")
              )
              .fetchOne(DSL.field("id", String::class.java))
          ).isEqualTo("id-pipeline001")

          // delete the pipeline
          sqlStorageService.deleteObject(ObjectType.PIPELINE, "id-pipeline001")

          expectThrows<NotFoundException> {
            sqlStorageService.loadObject<Pipeline>(ObjectType.PIPELINE, "id-pipeline001")
          }
        }

        test("bulk load pipelines") {
          val objectKeys = mutableSetOf<String>()
          (1..10).forEach {
            val objectKey = "id-pipeline00$it"
            objectKeys.add(objectKey)

            sqlStorageService.storeObject(
              ObjectType.PIPELINE,
              objectKey,
              Pipeline().apply {
                id = objectKey
                name = "pipeline00$it"
                lastModified = 100

                put("application", "application001")
              }
            )
          }

          val pipelines = sqlStorageService.loadObjects<Pipeline>(
            ObjectType.PIPELINE,
            (objectKeys + "does_not_exist").toList()
          )
          expectThat(
            pipelines.map { it.id }.toSet()
          ).isEqualTo(objectKeys)
        }
      }

      context("Entity Tags") {
        test("create, update and delete an entity tag") {
          // verify that entity tags can be created
          sqlStorageService.storeObject(
            ObjectType.ENTITY_TAGS,
            "id-entitytags001",
            EntityTags().apply {
              id = "id-entitytags001"
              lastModified = 100

              entityRef = EntityTags.EntityRef().apply {
                entityId = "application001"
              }
            }
          )

          var entityTags = sqlStorageService.loadObject<EntityTags>(ObjectType.ENTITY_TAGS, "id-entitytags001")
          expectThat(entityTags.entityRef.entityId).isEqualTo("application001")

          // verify that entity tags can be updated
          sqlStorageService.storeObject(
            ObjectType.ENTITY_TAGS,
            "id-entitytags001",
            EntityTags().apply {
              id = "id-entitytags001"
              lastModified = 200

              entityRef = EntityTags.EntityRef().apply {
                entityId = "application002"
              }
            }
          )

          entityTags = sqlStorageService.loadObject<EntityTags>(ObjectType.ENTITY_TAGS, "id-entitytags001")
          expectThat(entityTags.entityRef.entityId).isEqualTo("application002")

          // entity tags are _not_ versioned so only the most recent should be returned
          val entityTagsVersions = sqlStorageService.listObjectVersions<EntityTags>(
            ObjectType.ENTITY_TAGS, "id-entitytags001", 5
          )
          expectThat(entityTagsVersions.size).isEqualTo(1)
          expectThat(entityTagsVersions.get(0).entityRef.entityId).isEqualTo("application002")

          // delete the entity tags
          sqlStorageService.deleteObject(ObjectType.ENTITY_TAGS, "id-entitytags001")

          expectThrows<NotFoundException> {
            sqlStorageService.loadObject<EntityTags>(ObjectType.ENTITY_TAGS, "id-entitytags001")
          }

          // Verify we can store more than object
          sqlStorageService.storeObjects(
            ObjectType.ENTITY_TAGS,
            listOf(
              EntityTags().apply {
                id = "id-entitytags1of1"
                lastModified = 100
                lastModifiedBy = "anonymous"
                entityRef = EntityTags.EntityRef().apply {
                  entityId = "application001"
                }
              },
              EntityTags().apply {
                id = "id-entitytags1of2"
                lastModified = 100
                lastModifiedBy = "anonymous"
                entityRef = EntityTags.EntityRef().apply {
                  entityId = "application001"
                }
              }

            )
          )
          // Retrieve tags saved above
          entityTags = sqlStorageService.loadObject<EntityTags>(ObjectType.ENTITY_TAGS, "id-entitytags1of1")
          expectThat(entityTags.id).isEqualTo("id-entitytags1of1")

          entityTags = sqlStorageService.loadObject<EntityTags>(ObjectType.ENTITY_TAGS, "id-entitytags1of2")
          expectThat(entityTags.id).isEqualTo("id-entitytags1of2")
        }
      }
    }

    after {
      jooq.flushAll()
    }

    afterAll {
      jooq.close()
    }
  }

  fun tests() = rootContext<JooqConfig> {

    fixture {
      JooqConfig(SQLDialect.MYSQL, "jdbc:tc:mysql:5.7.22://somehostname:someport/databasename")
    }

    context("mysql CRUD operations") {
      crudOperations(JooqConfig(SQLDialect.MYSQL, "jdbc:tc:mysql:5.7.22://somehostname:someport/databasename"))
    }

    context("postgresql CRUD operations") {
      crudOperations(JooqConfig(SQLDialect.POSTGRES, "jdbc:tc:postgresql:12-alpine://somehostname:someport/databasename"))
    }
  }
}
