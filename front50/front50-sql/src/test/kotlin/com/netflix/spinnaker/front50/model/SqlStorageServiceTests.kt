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
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.config.Front50SqlProperties
import com.netflix.spinnaker.front50.api.model.Timestamped
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.tag.EntityTags
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import dev.minutest.ContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isTrue
import java.time.Instant

internal object SqlStorageServiceTests : JUnit5Minutests {

  class JooqConfig(val dialect: SQLDialect, val jdbcUrl: String)

  fun ContextBuilder<JooqConfig>.crudOperations(jooqConfig: JooqConfig) {

    val jooq = initDatabase(
      jooqConfig.jdbcUrl,
      jooqConfig.dialect
    )

    val registry = DefaultRegistry()

    val sqlStorageService = SqlStorageService(
      ObjectMapper(),
      registry,
      jooq,
      Clock.systemDefaultZone(),
      SqlRetryProperties(),
      1,
      "default",
      Front50SqlProperties()
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
          expectThat(application.createdAt).isNotNull()

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

          val allApplications = sqlStorageService.loadObjects<Application>(
            ObjectType.APPLICATION,
            sqlStorageService.listObjectKeys(ObjectType.APPLICATION).keys.toList()
          )
          expectThat(allApplications).isNotEmpty()
          allApplications.forEach {
            expectThat(it.createdAt).isNotNull()
          }
        }
      }

      context("Pipeline") {

        after {
          registry.reset()
        }

        test("create, update and delete a pipeline") {
          // verify that a pipeline can be created
          sqlStorageService.storeObject(
            ObjectType.PIPELINE,
            "id-pipeline001",
            Pipeline().apply {
              this.setName("pipeline001")
              this.setLastModified(100)
              this.setApplication("application001")
            }
          )

          var pipeline = sqlStorageService.loadObject<Pipeline>(ObjectType.PIPELINE, "id-pipeline001")
          expectThat(pipeline.getName()).isEqualTo("pipeline001")

          // verify that a pipeline can be updated
          sqlStorageService.storeObject(
            ObjectType.PIPELINE,
            "id-pipeline001",
            Pipeline().apply {
              this.setName("pipeline001_updated")
              this.setLastModified(200)
              this.setApplication("application001_updated")
            }
          )

          pipeline = sqlStorageService.loadObject(ObjectType.PIPELINE, "id-pipeline001")
          expectThat(pipeline.getName()).isEqualTo("pipeline001_updated")

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

        test("bulk create pipelines atomically") {
          // verify that pipelines can be bulk created
          val pipelines = (1..500).map { idx ->
            Pipeline().apply {
              id = "pipeline${idx}"
              name = "pipeline${idx}"
              lastModified = 100 + idx.toLong()
              lastModifiedBy = "test"
              setApplication("application")
            }
          }

          // set lastModifiedBy of one of the pipelines to null in order to force an error
          // and make sure no pipelines are added since additions are done in a single transaction
          pipelines[250].lastModifiedBy = null
          expectThrows<DataAccessException> {
            sqlStorageService.storeObjects(ObjectType.PIPELINE,pipelines)
            expectThat(
              jooq.selectCount().from("pipelines").fetchOne(0, Int::class.java)
            ).isEqualTo(0)
          }

          // Reset lastModifiedBy to ensure successful bulk creation
          pipelines[250].lastModifiedBy = "test"
          sqlStorageService.storeObjects(ObjectType.PIPELINE,pipelines)

          val storedPipelines = sqlStorageService.loadObjects<Pipeline>(ObjectType.PIPELINE, pipelines.map { it.id });
          expectThat(storedPipelines.size).isEqualTo(500);
          expectThat(storedPipelines.map { it.id }).isEqualTo(pipelines.map { it.id })
        }

        var lastModifiedMs : Long = 100
        test("loadObjects basic behavior") {
          val objectKeys = mutableSetOf<String>()
          val lastModifiedList = mutableSetOf<Long>()
          (1..10).forEach {
            val objectKey = "id-pipeline00$it"
            objectKeys.add(objectKey)
            lastModifiedList.add(lastModifiedMs);

            sqlStorageService.storeObject(
              ObjectType.PIPELINE,
              objectKey,
              Pipeline().apply {
                this.setId(objectKey)
                this.setName("pipeline00$it")
                this.setLastModified(lastModifiedMs)

                this.setApplication("application001")
              }
            )

            lastModifiedMs+=(100..1000).random()
          }

          val pipelines = sqlStorageService.loadObjects<Pipeline>(
            ObjectType.PIPELINE,
            (objectKeys + "does_not_exist").toList()
          )
          expectThat(
            pipelines.map { it.id }.toSet()
          ).isEqualTo(objectKeys)
          expectThat(
            pipelines.map { it.lastModified }.toSet()
          ).isEqualTo(lastModifiedList)
          expectThat(registry.counter("sqlStorageService.invalidJson", "objectType", "pipelines").count()).isEqualTo(0);
        }

        test("loadObjects with malformed pipelines") {
          // populate one record that fails to deserialize
          val lastModified: Long = Instant.now().toEpochMilli()

          // Can't use storeObject since it serializes a valid object...
          val invalidObjectKey = "new-id-pipeline001-busted"
          val bustedPipeline = mapOf("id" to invalidObjectKey,
                                     "name" to "new-pipeline001-busted",
                                     "application" to "application001",
                                     "body" to "not json",
                                     "created_at" to lastModified,
                                     "last_modified_at" to lastModified,
                                     "last_modified_by" to "test-user",
                                     "is_deleted" to false)
          jooq.insertInto(table("pipelines"), *bustedPipeline.keys.map { field(it) }.toTypedArray())
            .values(bustedPipeline.values)
            .execute()

          val onlyInvalid = sqlStorageService.loadObjects<Pipeline>(
            ObjectType.PIPELINE,
            listOf(invalidObjectKey)
          )
          expectThat(onlyInvalid).isEmpty()
          expectThat(registry.counter("sqlStorageService.invalidJson", "objectType", "pipelines").count()).isEqualTo(1);

          // Add a valid pipeline and repeat.  Make sure we get only the valid pipeline.
          val validObjectKey = "new-id-pipeline002-valid"
          sqlStorageService.storeObject(
            ObjectType.PIPELINE,
            validObjectKey,
            Pipeline().apply {
              this.setId(validObjectKey)
              this.setName("new-pipeline002")
              this.setLastModified(lastModified)
              this.setApplication("application001")
            }
          )

          val withValidPipeline = sqlStorageService.loadObjects<Pipeline>(
            ObjectType.PIPELINE,
            listOf(invalidObjectKey, validObjectKey)
          )
          expectThat(withValidPipeline.map { it.id }.toList()).isEqualTo(listOf(validObjectKey))
          expectThat(registry.counter("sqlStorageService.invalidJson", "objectType", "pipelines").count()).isEqualTo(2);
        }

        test("loadObjectsNewerThan basic behavior") {
          // populate 10 records <= the threshold and 5 newer than the threshold
          // make sure loadObjectsNewerThan returns 5
          val lastModifiedThreshold: Long = Instant.now().toEpochMilli()
          val oldObjectKeys = mutableSetOf<String>()
          val newObjectKeys = mutableSetOf<String>()

          val numOldObjects = 10
          val numNewObjects = 5
          expectThat(numOldObjects).isNotEqualTo(numNewObjects)

          // Populate the records <= the threshold
          (1..numOldObjects).forEach {
            val objectKey = "old-id-pipeline00$it"
            oldObjectKeys.add(objectKey)
            sqlStorageService.storeObject(
              ObjectType.PIPELINE,
              objectKey,
              Pipeline().apply {
                this.setId(objectKey)
                this.setName("old-pipeline00$it")
                this.setLastModified(lastModifiedThreshold - (it - 1))
                this.setApplication("application001")
              }
            )
          }

          // Populate the records > the threshold
          (1..numNewObjects).forEach {
            val objectKey = "new-id-pipeline00$it"
            newObjectKeys.add(objectKey)
            sqlStorageService.storeObject(
              ObjectType.PIPELINE,
              objectKey,
              Pipeline().apply {
                this.setId(objectKey)
                this.setName("new-pipeline00$it")
                this.setLastModified(lastModifiedThreshold + it)
                this.setApplication("application001")
              }
            )
          }

          // Verify basic behavior, that it only returns the newer items
          val newerItems: Map<String, List<Pipeline>> = sqlStorageService.loadObjectsNewerThan(
            ObjectType.PIPELINE,
            lastModifiedThreshold
          )
          verifyNewerThan(newerItems, newObjectKeys, emptySet())
          expectThat(registry.counter("sqlStorageService.invalidJson", "objectType", "pipelines").count()).isEqualTo(0);

          // Delete a newer item and verify the behavior
          val newIdToDelete = newObjectKeys.first()
          expectThat(newObjectKeys.remove(newIdToDelete)).isTrue()
          sqlStorageService.deleteObject(ObjectType.PIPELINE, newIdToDelete);
          val afterDeleteNewer: Map<String, List<Pipeline>> = sqlStorageService.loadObjectsNewerThan(
            ObjectType.PIPELINE,
            lastModifiedThreshold
          )
          verifyNewerThan(afterDeleteNewer, newObjectKeys, setOf(newIdToDelete))

          // Delete an older item and verify the behavior
          var oldIdToDelete = oldObjectKeys.first()
          expectThat(oldObjectKeys.remove(oldIdToDelete)).isTrue()
          sqlStorageService.deleteObject(ObjectType.PIPELINE, oldIdToDelete);
          val afterDeleteOlder: Map<String, List<Pipeline>> = sqlStorageService.loadObjectsNewerThan(
            ObjectType.PIPELINE,
            lastModifiedThreshold
          )
          verifyNewerThan(afterDeleteOlder, newObjectKeys, setOf(newIdToDelete, oldIdToDelete))

          // Modify an older item and verify the behavior
          val oldIdToModify = oldObjectKeys.first()
          expectThat(oldObjectKeys.remove(oldIdToModify)).isTrue()
          val oldItemToModify: Pipeline = sqlStorageService.loadObject(ObjectType.PIPELINE, oldIdToModify);
          oldItemToModify.lastModified = lastModifiedThreshold + 10; // 10 is an arbitrary amount
          sqlStorageService.storeObject(ObjectType.PIPELINE, oldIdToModify, oldItemToModify);
          val afterModifyOlder: Map<String, List<Pipeline>> = sqlStorageService.loadObjectsNewerThan(
            ObjectType.PIPELINE,
            lastModifiedThreshold
          )
          verifyNewerThan(afterModifyOlder, newObjectKeys + oldIdToModify, setOf(newIdToDelete, oldIdToDelete))
        }

        test("loadObjectsNewerThan with malformed pipelines") {
          // populate one record that's newer than the threshold that fails to deserialize
          val lastModifiedThreshold: Long = Instant.now().toEpochMilli()

          // Can't use storeObject since it serializes a valid object...
          val bustedPipeline = mapOf("id" to "new-id-pipeline001-busted",
                                     "name" to "new-pipeline001-busted",
                                     "application" to "application001",
                                     "body" to "not json",
                                     "created_at" to lastModifiedThreshold + 1,
                                     "last_modified_at" to lastModifiedThreshold + 1,
                                     "last_modified_by" to "test-user",
                                     "is_deleted" to false)
          jooq.insertInto(table("pipelines"), *bustedPipeline.keys.map { field(it) }.toTypedArray())
            .values(bustedPipeline.values)
            .execute()

          val onlyInvalid: Map<String, List<Pipeline>> = sqlStorageService.loadObjectsNewerThan(
            ObjectType.PIPELINE,
            lastModifiedThreshold
          )
          verifyNewerThan(onlyInvalid, setOf(), setOf())
          expectThat(registry.counter("sqlStorageService.invalidJson", "objectType", "pipelines").count()).isEqualTo(1);

          // Add a valid pipeline and repeat.  Make sure we get only the valid pipeline.
          val objectKey = "new-id-pipeline002-valid"
          sqlStorageService.storeObject(
            ObjectType.PIPELINE,
            objectKey,
            Pipeline().apply {
              this.setId(objectKey)
              this.setName("new-pipeline002")
              this.setLastModified(lastModifiedThreshold + 1)
              this.setApplication("application001")
            }
          )

          val withValidPipeline: Map<String, List<Pipeline>> = sqlStorageService.loadObjectsNewerThan(
            ObjectType.PIPELINE,
            lastModifiedThreshold
          )
          verifyNewerThan(withValidPipeline, setOf(objectKey), setOf())
          expectThat(registry.counter("sqlStorageService.invalidJson", "objectType", "pipelines").count()).isEqualTo(2);
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

  }

  private fun <T : Timestamped> verifyNewerThan(newerThanResult: Map<String, List<T>>, expectedModifiedIds: Set<String>, expectedDeletedIds: Set<String>): Unit {
    val modifiedItems: List<T>? = newerThanResult.get("not_deleted")
    val deletedItems: List<T>? = newerThanResult.get("deleted")
    expectThat(modifiedItems!!).hasSize(expectedModifiedIds.size)
    expectThat(deletedItems!!).hasSize(expectedDeletedIds.size)
    val modifiedIds: Set<String> = modifiedItems.map { it.id }.toSet()
    expectThat(modifiedIds).isEqualTo(expectedModifiedIds)
    val deletedIds: Set<String> = deletedItems.map { it.id }.toSet()
    expectThat(deletedIds).isEqualTo(expectedDeletedIds)
  }

  fun tests() = rootContext<JooqConfig> {

    fixture {
      JooqConfig(SQLDialect.MYSQL, "jdbc:tc:mysql:8.0.40://somehostname/databasename")
    }

    context("mysql CRUD operations") {
      crudOperations(JooqConfig(SQLDialect.MYSQL, "jdbc:tc:mysql:8.0.40://somehostname/databasename"))
    }

    context("postgresql CRUD operations") {
      crudOperations(JooqConfig(SQLDialect.POSTGRES, "jdbc:tc:postgresql:12-alpine://somehostname/databasename"))
    }
  }
}
