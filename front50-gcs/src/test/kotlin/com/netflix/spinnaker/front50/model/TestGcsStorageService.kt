/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnnaker.front50.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.common.util.concurrent.MoreExecutors
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.GcsStorageService
import com.netflix.spinnaker.front50.model.ObjectType
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.delivery.Delivery
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsKeys
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isEqualToIgnoringCase
import strikt.assertions.isFailure
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isSuccess
import strikt.assertions.isTrue
import strikt.assertions.startsWith
import strikt.assertions.endsWith

class GcsStorageServiceTest {

  companion object {
    private const val BUCKET_NAME = "myBucket"
    private const val BUCKET_LOCATION = "bucketLocation"
    private const val BASE_PATH = "my/base/path"
    private val DATA_FILENAME = "specification.json";
    private val PERMISSION_DATA_FILENAME = "permission.json";

    @JvmStatic
    fun objectTypes() : List<Array<Any>> {
      return listOf(
        arrayOf(ObjectType.PROJECT.group, ObjectType.PROJECT, """{"name": "APP NAME","email": "sample@example.com"}"""),
        arrayOf(ObjectType.PIPELINE.group,ObjectType.PIPELINE, """{"name": "APP NAME","email": "sample@example.com"}"""),
        arrayOf(ObjectType.STRATEGY.group,ObjectType.STRATEGY, """{"name": "APP NAME","email": "sample@example.com"}"""),
        arrayOf(ObjectType.PIPELINE_TEMPLATE.group,ObjectType.PIPELINE_TEMPLATE, """{"name": "APP NAME","email": "sample@example.com"}"""),
        arrayOf(ObjectType.NOTIFICATION.group,ObjectType.NOTIFICATION, """{"name": "APP NAME","email": "sample@example.com"}"""),
        arrayOf(ObjectType.SERVICE_ACCOUNT.group,ObjectType.SERVICE_ACCOUNT, """{"name": "ServiceAccount","memberOf": ["myApp-prod","myApp-qa"]}"""),
        arrayOf(ObjectType.APPLICATION.group,ObjectType.APPLICATION, """{"name": "APP NAME","email": "sample@example.com"}"""),
        arrayOf(ObjectType.SNAPSHOT.group,ObjectType.SNAPSHOT, """{"application": "APP NAME","account": "someAccount"}"""),
        arrayOf(ObjectType.ENTITY_TAGS.group,ObjectType.ENTITY_TAGS, """{"idPattern": "entityType__entityId__account__region"}"""),
        arrayOf(ObjectType.DELIVERY.group,ObjectType.DELIVERY, """{"application": "APP NAME"}"""),
        arrayOf(ObjectType.PLUGIN_INFO.group,ObjectType.PLUGIN_INFO, """{"description": "APP NAME","provider": "github"}"""),
        arrayOf(ObjectType.PLUGIN_VERSIONS.group,ObjectType.PLUGIN_VERSIONS, """{"serverGroupName": "myapp","location": "us-west-2"}""")
      )
    }
  }

  private lateinit var gcs: Storage
  private lateinit var executor: ExecutorService
  private lateinit var clock: SettableClock
  private lateinit var storageService: GcsStorageService

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    clock = SettableClock()
    gcs = when {
      testInfo.tags.contains("mockGcs") -> mockk()
      else -> {
        val service = StorageOptions.newBuilder().setServiceRpcFactory(FakeStorageRpcFactory(clock)).build().service
        service.create(Bucket.of(BUCKET_NAME))
        service
      }
    }
    executor = when {
      testInfo.tags.contains("testExecutor") -> ControlledExecutor()
      testInfo.tags.contains("realExecutor") -> Executors.newCachedThreadPool()
      else -> MoreExecutors.newDirectExecutorService()
    }
    storageService = createStorageService(gcs, executor)
  }

  @MockGcs("LocalStorageHelper doesn't support bucket create/get operations")
  @Test
  fun `ensureBucketExists with no bucket`() {

    every { gcs.get(any<String>(), *anyVararg()) } returns null
    every { gcs.create(any<BucketInfo>(), *anyVararg()) } returns mockk()

    storageService.ensureBucketExists()

    val bucketInfo = slot<BucketInfo>()

    verify { gcs.create(capture(bucketInfo), *anyVararg()) }
    expectThat(bucketInfo.captured.location).isEqualTo(BUCKET_LOCATION)
    expectThat(bucketInfo.captured.versioningEnabled()).isTrue()
  }

  private fun createStorageService(gcs: Storage, executor: ExecutorService) =
    GcsStorageService(gcs, BUCKET_NAME, BUCKET_LOCATION, BASE_PATH, DATA_FILENAME, ObjectMapper(), executor)

  @MockGcs("FakeStorageRpc doesn't support bucket operations")
  @Test
  fun `ensureBucketExists with existing bucket`() {

    val mockBucket = mockk<Bucket>()
    every { mockBucket.versioningEnabled() } returns true
    every { gcs.get(any<String>(), *anyVararg()) } returns mockBucket

    storageService.ensureBucketExists()

    verify(exactly = 0) { gcs.create(any<BucketInfo>(), *anyVararg()) }
  }

  @MockGcs("FakeStorageRpc doesn't support bucket operations")
  @Test
  fun `supportsVersioning all three options`() {

    val mockBucket: Bucket = mockk()
    every { gcs.get(any<String>(), *anyVararg()) } returns mockBucket

    every { mockBucket.versioningEnabled() } returns false
    expectThat(storageService.supportsVersioning()).isFalse()

    every { mockBucket.versioningEnabled() } returns null
    expectThat(storageService.supportsVersioning()).isFalse()

    every { mockBucket.versioningEnabled() } returns true
    expectThat(storageService.supportsVersioning()).isTrue()
  }

  @Test
  fun `loadObject without object throws NotFoundException`() {

    expectCatching {
      storageService.loadObject<Application>(ObjectType.APPLICATION, "plumpstuff")
    }
      .isFailure()
      .isA<NotFoundException>()
  }

  @Test
  fun `loadObject fetches previously stored data - ApplicationPermissions`() {

    val path = "$BASE_PATH/${ObjectType.APPLICATION_PERMISSION.group}/plumpstuff/$PERMISSION_DATA_FILENAME"
    writeFile(
      path,
      """
        {
          "name": "APP NAME",
          "permissions": {}
        }
      """
    )

    val applicationPermission: Application.Permission = storageService.loadObject(ObjectType.APPLICATION_PERMISSION, "plumpstuff")

    expectThat(applicationPermission.name).isEqualTo("APP NAME")
  }

  @ParameterizedTest(name = "loadObject fetches previously stored data of {0}")
  @MethodSource("objectTypes")
  fun `loadObject fetches previously stored data - All Types`(group: String, objectType: ObjectType, content: String) {
    val path = "$BASE_PATH/${objectType.group}/plumpstuff/$DATA_FILENAME"
    writeFile(
      path,
      content
    )
    expectCatching {
      val type: Any = storageService.loadObject(objectType, "plumpstuff")
    }.isSuccess()
  }

  @Test
  fun `loadObject sets updated time on object`() {

    clock.setEpochMilli(123L)
    val path = "$BASE_PATH/${ObjectType.APPLICATION.group}/plumpstuff/$DATA_FILENAME"
    writeFile(path, "{}")

    val application: Application = storageService.loadObject(ObjectType.APPLICATION, "plumpstuff")

    expectThat(application.updateTs).isEqualTo("123")
  }

  @Test
  fun `deleteObject on non-existent object`() {

    // Store an unrelated object to set the lastModifiedTime for applications
    clock.setEpochMilli(123L)
    storageService.storeObject(ObjectType.APPLICATION, "unrelated", Application())

    // Now delete an application that doesn't exist and make sure it suceeds without an error
    clock.setEpochMilli(456L)
    expectCatching {
      storageService.deleteObject(ObjectType.APPLICATION, "plumpstuff")
    }.isSuccess()

    // And make sure that the lastModifiedTime wasn't changed in this case
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(123L)
  }

  @Test
  fun `deleteObject on existing object`() {

    clock.setEpochMilli(123L)
    storageService.storeObject(ObjectType.APPLICATION, "plumpstuff", Application())

    clock.setEpochMilli(456L)
    storageService.deleteObject(ObjectType.APPLICATION, "plumpstuff")

    expectCatching { storageService.loadObject<Application>(ObjectType.APPLICATION, "plumpstuff") }
      .isFailure().isA<NotFoundException>()
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(456L)
  }

  @Test
  fun `storeObject writes metadata`() {

    storageService.storeObject(ObjectType.APPLICATION, "plumpstuff", Application())

    val path = "$BASE_PATH/${ObjectType.APPLICATION.group}/plumpstuff/$DATA_FILENAME"
    val blob = gcs.get(BlobId.of(BUCKET_NAME, path))

    expectThat(blob).isNotNull()
    expectThat(blob!!.contentType).isEqualTo("application/json")
    expectThat(blob.name).endsWith("/$DATA_FILENAME")
  }

  @Test
  fun `round-trip data`() {
    val application = Application().apply {
      name = "APP NAME"
      email = "sample@example.com"
      description = "sample description"
    }

    storageService.storeObject(ObjectType.APPLICATION, "plumpstuff", application)

    val loaded: Application = storageService.loadObject(ObjectType.APPLICATION, "plumpstuff")

    expectThat(loaded.name).isEqualTo("APP NAME")
    expectThat(loaded.email).isEqualTo("sample@example.com")
    expectThat(loaded.description).isEqualTo("sample description")
  }

  @Test
  fun `listObjectKeys basic test`() {

    storageService.storeObject(ObjectType.APPLICATION, "app1", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app2", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app3", Application())
    storageService.storeObject(ObjectType.APPLICATION_PERMISSION,"app3",Application.Permission())
    val keys = storageService.listObjectKeys(ObjectType.APPLICATION)
    val keysWithPermissions = storageService.listObjectKeys(ObjectType.APPLICATION_PERMISSION)

    expectThat(keys).containsKeys("app1", "app2", "app3")
    expectThat(keysWithPermissions).containsKeys("app3")
  }

  @Test
  fun `listObjectKeys ignores other types`() {

    storageService.storeObject(ObjectType.APPLICATION, "app1", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app2", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app3", Application())
    storageService.storeObject(ObjectType.DELIVERY, "delivery", Delivery())
    storageService.storeObject(ObjectType.PIPELINE, "pipeline", Pipeline())
    storageService.storeObject(ObjectType.APPLICATION_PERMISSION,"app4",Application.Permission())


    val keys = storageService.listObjectKeys(ObjectType.APPLICATION)

    expectThat(keys).containsKeys("app1", "app2", "app3")
  }

  @Test
  fun `listObjectKeys ignores similar filenames`() {

    writeEmptyFile("$BASE_PATH/${ObjectType.APPLICATION.group}/app1/unknownFilename.txt")
    writeEmptyFile("$BASE_PATH${ObjectType.APPLICATION.group}/app2/$DATA_FILENAME")
    writeEmptyFile("$BASE_PATH/${ObjectType.APPLICATION.group}/app3/$DATA_FILENAME")
    writeEmptyFile("$BASE_PATH/${ObjectType.APPLICATION.group}app4/$DATA_FILENAME")
    writeEmptyFile("$BASE_PATH/${ObjectType.APPLICATION.group}/app5$DATA_FILENAME")

    val keys = storageService.listObjectKeys(ObjectType.APPLICATION)

    expectThat(keys).containsKeys("app3")
  }

  @Test
  fun `listObjectVersions basic test`() {

    clock.setEpochMilli(111L)
    storageService.storeObject(ObjectType.APPLICATION, "plumpstuff", Application().apply { name = "version1" })
    storageService.storeObject(ObjectType.APPLICATION_PERMISSION, "plumpstuff", Application.Permission().apply { name = "versionPerm1" })

    clock.setEpochMilli(222L)
    storageService.storeObject(ObjectType.APPLICATION, "plumpstuff", Application().apply { name = "version2" })
    storageService.storeObject(ObjectType.APPLICATION_PERMISSION, "plumpstuff", Application.Permission().apply { name = "versionPerm2" })
    clock.setEpochMilli(333L)
    storageService.storeObject(ObjectType.APPLICATION, "plumpstuff", Application().apply { name = "version3" })
    storageService.storeObject(ObjectType.APPLICATION_PERMISSION, "plumpstuff", Application.Permission().apply { name = "versionPerm3" })

    val versions: List<Application> =
      storageService.listObjectVersions<Application>(ObjectType.APPLICATION, "plumpstuff", 100).toList()
    val permVersions: List<Application.Permission> =
      storageService.listObjectVersions<Application.Permission>(ObjectType.APPLICATION_PERMISSION, "plumpstuff", 100).toList()
    expectThat(versions).hasSize(3)
    expectThat(versions[0].name).isEqualToIgnoringCase("version3")
    expectThat(versions[0].updateTs).isEqualTo("333")
    expectThat(versions[1].name).isEqualToIgnoringCase("version2")
    expectThat(versions[1].updateTs).isEqualTo("222")
    expectThat(versions[2].name).isEqualToIgnoringCase("version1")
    expectThat(versions[2].updateTs).isEqualTo("111")

    expectThat(permVersions).hasSize(3)
    expectThat(permVersions[0].name).isEqualToIgnoringCase("versionPerm3")
    expectThat(permVersions[0].lastModified).isEqualTo(333)
    expectThat(permVersions[1].name).isEqualToIgnoringCase("versionPerm2")
    expectThat(permVersions[1].lastModified).isEqualTo(222)
    expectThat(permVersions[2].name).isEqualToIgnoringCase("versionPerm1")
    expectThat(permVersions[2].lastModified).isEqualTo(111)
  }

  @Test
  fun `listObjectVersions ignores similar filenames`() {

    writeFile("$BASE_PATH/${ObjectType.APPLICATION.group}/plumpstuff/$DATA_FILENAME", """{ "name": "the good one" }""")
    writeFile("$BASE_PATH/${ObjectType.APPLICATION.group}/plumpstuff/" +
      ObjectType.APPLICATION_PERMISSION.getDefaultMetadataFilename(true), """{ "name": "the good one but permissions file" }""")
    writeFile("$BASE_PATH/${ObjectType.APPLICATION.group}/plumpstuff/unknownFilename.txt", """{}""")
    writeFile("$BASE_PATH${ObjectType.APPLICATION.group}/plumpstuff/$DATA_FILENAME", """{}""")
    writeFile("$BASE_PATH/${ObjectType.APPLICATION.group}plumpstuff/$DATA_FILENAME", """{}""")
    writeFile("$BASE_PATH/${ObjectType.APPLICATION.group}/plumpstuff$DATA_FILENAME", """{}""")

    val versions: List<Application> =
      storageService.listObjectVersions<Application>(ObjectType.APPLICATION, "plumpstuff", 100).toList()

    expectThat(versions).hasSize(1)
    expectThat(versions[0].name).isEqualToIgnoringCase("the good one")
  }

  @Test
  fun `listObjectVersions ignores other objects`() {

    storageService.storeObject(ObjectType.APPLICATION, "app1", Application().apply { name = "app1v1" })
    storageService.storeObject(ObjectType.APPLICATION, "app1", Application().apply { name = "app1v2" })
    storageService.storeObject(ObjectType.APPLICATION, "app1", Application().apply { name = "app1v3" })
    storageService.storeObject(ObjectType.APPLICATION_PERMISSION, "app1", Application.Permission().apply { name = "perm1" })
    storageService.storeObject(ObjectType.APPLICATION, "app2", Application().apply { name = "app2" })
    storageService.storeObject(ObjectType.APPLICATION, "app3", Application().apply { name = "app3" })
    storageService.storeObject(ObjectType.PIPELINE, "app1", Application().apply { name = "app1" })
    storageService.storeObject(ObjectType.PIPELINE, "app2", Application().apply { name = "app2" })

    val versions: List<Application> =
      storageService.listObjectVersions<Application>(ObjectType.APPLICATION, "app1", 100).toList()

    expectThat(versions).hasSize(3)
    expectThat(versions).all { get { name.toLowerCase() }.startsWith("app1") }
  }

  @Test
  fun `listObjectVersions with limit`() {

    for (i: Long in 1L..100) {
      clock.setEpochMilli(i)
      storageService.storeObject(ObjectType.APPLICATION, "app", Application().apply { name = "version$i" })
    }

    val versions: List<Application> =
      storageService.listObjectVersions<Application>(ObjectType.APPLICATION, "app", 3).toList()

    expectThat(versions).hasSize(3)
    expectThat(versions[0].name).isEqualToIgnoringCase("version100")
    expectThat(versions[0].updateTs).isEqualTo("100")
    expectThat(versions[1].name).isEqualToIgnoringCase("version99")
    expectThat(versions[1].updateTs).isEqualTo("99")
    expectThat(versions[2].name).isEqualToIgnoringCase("version98")
    expectThat(versions[2].updateTs).isEqualTo("98")
  }

  @Test
  fun `getLastModified returns 0 if not found`() {
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(0)
  }

  @Test
  @TestExecutor
  fun `multiple simultaneous updates only result in a single update to last-modified file`() {

    clock.setEpochMilli(123L)
    storageService.storeObject(ObjectType.APPLICATION, "app1", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app2", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app3", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app4", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app5", Application())

    val executor = executor as ControlledExecutor
    expectThat(executor.taskCount()).isEqualTo(1)
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(0)
    executor.runNext()
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(123)
  }

  @Test
  @TestExecutor
  fun `different object types schedule different tasks`() {

    storageService.storeObject(ObjectType.APPLICATION, "app1", Application())
    storageService.storeObject(ObjectType.PIPELINE, "pipeline1", Pipeline())
    storageService.storeObject(ObjectType.APPLICATION, "app2", Application())
    storageService.storeObject(ObjectType.PIPELINE, "pipeline2", Pipeline())
    storageService.storeObject(ObjectType.APPLICATION, "app3", Application())
    storageService.storeObject(ObjectType.PIPELINE, "pipeline3", Pipeline())

    val executor = executor as ControlledExecutor
    expectThat(executor.taskCount()).isEqualTo(2)
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(0)
    expectThat(storageService.getLastModified(ObjectType.PIPELINE)).isEqualTo(0)
    clock.setEpochMilli(123)
    executor.runNext()
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(123)
    expectThat(storageService.getLastModified(ObjectType.PIPELINE)).isEqualTo(0)
    clock.setEpochMilli(456)
    executor.runNext()
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(123)
    expectThat(storageService.getLastModified(ObjectType.PIPELINE)).isEqualTo(456)
  }

  @Test
  @TestExecutor
  fun `getLastModified schedules an update if last-modified file doesn't exist`() {

    clock.setEpochMilli(123L)
    val lastModified = storageService.getLastModified(ObjectType.APPLICATION)

    val executor = executor as ControlledExecutor
    expectThat(executor.taskCount()).isEqualTo(1)
    expectThat(lastModified).isEqualTo(0)
    executor.runNext()
    expectThat(storageService.getLastModified(ObjectType.APPLICATION)).isEqualTo(123)
  }

  @Test
  @TestExecutor
  fun `getLastModified doesn't schedule an update if last-modified exists`() {

    val executor = executor as ControlledExecutor

    clock.setEpochMilli(123L)
    storageService.storeObject(ObjectType.APPLICATION, "app", Application())
    executor.runNext() // create the application/last-modified file

    storageService.getLastModified(ObjectType.APPLICATION)
    expectThat(executor.taskCount()).isEqualTo(0)
  }

  @Test
  @TestExecutor
  @MockGcs("need to have the update call take a long time")
  fun `waits for running task to finish before scheduling another`() {

    val executor = executor as ControlledExecutor

    // Let any storeObject() call finish, we don't care about the result
    every { gcs.create(any(), any<ByteArray>(), *anyVararg()) } returns mockk()

    val lock = ReentrantLock()
    val updateStarted = lock.newCondition()
    val finishUpdate = lock.newCondition()
    val updateTaskCompleted = lock.newCondition()

    // When the service tries to update last-modified, hold until we call `finishUpdate.signal()`
    every { gcs.update(any<BlobInfo>()) } answers {
      lock.lock()
      try {
        updateStarted.signal()
        finishUpdate.await()
        mockk()
      } finally {
        lock.unlock()
      }
    }

    // Now create an object, which should schedule a modification time update
    storageService.storeObject(ObjectType.APPLICATION, "app1", Application())

    // Now start that modification time update task in a separate thread. Since it calls our mock,
    // it won't finish until we call `finishUpdate.signal()`. Then, once the task has completed, it
    // will call `updateTaskCompleted.signal()`
    val updateThread = thread(start = false) {
      lock.lock()
      try {
        executor.runNext()
        updateTaskCompleted.signal()
      } finally {
        lock.unlock()
      }
    }

    lock.lock()
    try {
      updateThread.start()
      updateStarted.await()
    } finally {
      lock.unlock()
    }

    // With the modification time updater thread running, submit several more modifications.
    storageService.storeObject(ObjectType.APPLICATION, "app2", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app3", Application())
    storageService.storeObject(ObjectType.APPLICATION, "app4", Application())

    // There shouldn't be any queued tasks, because there is already an updater running
    expectThat(executor.taskCount()).isEqualTo(0)

    // Now finish the update call and wait for `updateTaskCompleted`.
    lock.lock()
    try {
      finishUpdate.signal()
      updateTaskCompleted.await()
    } finally {
      lock.unlock()
    }

    // Since the update task completed, we should immediate have another task queued.
    expectThat(executor.taskCount()).isEqualTo(1)
  }

  @Test
  @RealExecutor
  @MockGcs("need to have the update call wait a long time")
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun `doesn't wait for last-modified update to finish`() {

    // Let any storeObject() call finish, we don't care about the result
    every { gcs.create(any(), any<ByteArray>(), *anyVararg()) } returns mockk()

    // When the service tries to update last-modified, hold until we call `finishUpdate.signal()`
    every { gcs.update(any<BlobInfo>()) } answers { Thread.sleep(Long.MAX_VALUE); mockk() }

    expectCatching {
      storageService.storeObject(ObjectType.APPLICATION, "app", Application())
    }.isSuccess()
  }

  private fun writeEmptyFile(path: String) {
    writeFile(path, byteArrayOf())
  }

  private fun writeFile(path: String, content: String) {
    writeFile(path, content.toByteArray(Charsets.UTF_8))
  }

  private fun writeFile(path: String, content: ByteArray) {
    val blobId = BlobId.of(BUCKET_NAME, path)
    gcs.create(BlobInfo.newBuilder(blobId).build(), content)
  }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("mockGcs")
annotation class MockGcs(val reason: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("testExecutor")
annotation class TestExecutor

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("realExecutor")
annotation class RealExecutor

private class SettableClock(
  private var currentTime: Instant = Instant.ofEpochMilli(0L),
  private val zone: ZoneId = ZoneOffset.UTC
) : Clock() {

  override fun withZone(zone: ZoneId) = SettableClock(currentTime, zone)

  override fun instant() = currentTime

  override fun getZone() = zone

  fun setEpochMilli(epochMilli: Long) {
    currentTime = Instant.ofEpochMilli(epochMilli)
  }
}

private class ControlledExecutor : AbstractExecutorService() {
  private val runnables = java.util.ArrayDeque<Runnable>()

  override fun execute(command: Runnable) {
    synchronized(runnables) {
      runnables.add(command)
    }
  }

  fun runNext() {
    val runnable = synchronized(runnables) {
      runnables.pop()
    }
    runnable.run()
  }

  fun taskCount(): Int {
    return synchronized(runnables) { runnables.size }
  }

  override fun isTerminated(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun shutdown() {
    throw UnsupportedOperationException()
  }

  override fun shutdownNow(): List<Runnable> {
    throw UnsupportedOperationException()
  }

  override fun isShutdown(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
    throw UnsupportedOperationException()
  }
}
