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

import com.google.api.client.util.DateTime
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.Bucket
import com.google.api.services.storage.model.BucketAccessControl
import com.google.api.services.storage.model.HmacKey
import com.google.api.services.storage.model.HmacKeyMetadata
import com.google.api.services.storage.model.Notification
import com.google.api.services.storage.model.ObjectAccessControl
import com.google.api.services.storage.model.Policy
import com.google.api.services.storage.model.ServiceAccount
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.storage.model.TestIamPermissionsResponse
import com.google.cloud.Tuple
import com.google.cloud.spi.ServiceRpcFactory
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import com.google.cloud.storage.spi.v1.RpcBatch
import com.google.cloud.storage.spi.v1.StorageRpc
import com.google.cloud.storage.spi.v1.StorageRpc.Option
import com.google.common.io.ByteStreams
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock

internal class FakeStorageRpcFactory(private val clock: Clock) : ServiceRpcFactory<StorageOptions> {
  override fun create(options: StorageOptions?) = FakeStorageRpc(clock)
}

/**
 * This class is a fake that allows us to use just the parts of [com.google.cloud.storage.Storage]
 * that we need to test [GcsStorageService]. It's probably better than mocking everything, but just
 * barely.
 */
internal class FakeStorageRpc(private val clock: Clock) : StorageRpc {

  companion object {
    private val ALLOWED_LIST_OPTIONS = setOf(Option.PREFIX, Option.VERSIONS)
    private fun fullPath(storageObject: StorageObject): String = fullPath(storageObject.bucket, storageObject.name)
    private fun fullPath(bucket: String, name: String) = "$bucket/$name"
  }

  /**
   * Represents a collection of buckets.
   */
  private class Buckets {
    private val buckets: MutableMap<String, BucketContents> = mutableMapOf()

    operator fun get(bucket: String) = buckets[bucket]

    fun exists(bucket: String) = buckets.containsKey(bucket)

    fun create(bucket: String) {
      buckets[bucket] = BucketContents()
    }
  }

  /**
   * Represents the contents of a bucket.
   */
  private class BucketContents {
    private val objects: MutableMap<String, MutableList<Blob>> = mutableMapOf()

    fun getGenerations(storageObject: StorageObject) = objects.getOrPut(storageObject.name, { mutableListOf() })

    fun delete(storageObject: StorageObject) = objects.remove(storageObject.name) != null

    fun list(prefix: String) = objects.filter { (path, _) -> path.startsWith(prefix) }.values
  }

  private data class Blob(val storageObject: StorageObject, val content: ByteArray)

  private val buckets = Buckets()

  override fun create(storageObject: StorageObject, data: InputStream, options: MutableMap<Option, *>): StorageObject {
    if (options.isNotEmpty()) throw UnsupportedOperationException("unsupported options to create: ${options.keys}")
    if (storageObject.generation != null) throw UnsupportedOperationException("can't call create with a specific generation")
    val blobs = buckets[storageObject.bucket] ?: throw StorageException(404, "bucket ${storageObject.bucket} does not exist")
    val generations = blobs.getGenerations(storageObject)
    val stampedObject = storageObject.clone()
    stampedObject.generation = generations.size + 1L
    stampedObject.updated = DateTime(clock.millis())
    generations.add(Blob(stampedObject, ByteStreams.toByteArray(data)))
    return stampedObject
  }

  override fun patch(storageObject: StorageObject, options: MutableMap<Option, *>): StorageObject {
    if (options.isNotEmpty()) throw UnsupportedOperationException("unsupported options to patch: ${options.keys}")
    val blobs = buckets[storageObject.bucket] ?: throw StorageException(404, "bucket ${storageObject.bucket} does not exist")
    val generations = blobs.getGenerations(storageObject)
    if (generations.isEmpty()) throw StorageException(404, "no object ${fullPath(storageObject)}")
    val foundObject = generations[generations.size - 1].storageObject
    storageObject.forEach { (key, value) -> foundObject.set(key, value) }
    foundObject.updated = DateTime(clock.millis())
    return foundObject
  }

  override fun get(storageObject: StorageObject, options: MutableMap<Option, *>): StorageObject? {
    if (options.isNotEmpty()) throw UnsupportedOperationException("unsupported options to get: ${options.keys}")
    val blobs = buckets[storageObject.bucket] ?: return null
    val generations = blobs.getGenerations(storageObject)
    val generation = storageObject.generation ?: generations.size.toLong()
    if (generation == 0L || generation > generations.size) return null
    return generations[generation.toInt() - 1].storageObject
  }

  override fun load(storageObject: StorageObject, options: MutableMap<Option, *>): ByteArray? {
    if (options.isNotEmpty()) throw UnsupportedOperationException("unsupported options to load: ${options.keys}")
    val blobs = buckets[storageObject.bucket] ?: throw StorageException(404, "bucket ${storageObject.bucket} does not exist")
    val generations = blobs.getGenerations(storageObject)
    val generation = storageObject.generation ?: generations.size.toLong()
    if (generation == 0L || generation > generations.size) return null
    return generations[generation.toInt() - 1].content
  }

  override fun delete(storageObject: StorageObject, options: MutableMap<Option, *>): Boolean {
    if (options.isNotEmpty()) throw UnsupportedOperationException("unsupported options to delete: ${options.keys}")
    if (storageObject.generation != null) throw UnsupportedOperationException("can't delete generations")
    val blobs = buckets[storageObject.bucket] ?: return false
    return blobs.delete(storageObject)
  }

  override fun list(bucket: String, options: MutableMap<Option, *>): Tuple<String?, Iterable<StorageObject>> {
    val unsupportedOptions = options - ALLOWED_LIST_OPTIONS
    if (unsupportedOptions.isNotEmpty()) {
      throw java.lang.UnsupportedOperationException("unsupported options to list: $unsupportedOptions")
    }
    val prefix = options[Option.PREFIX] as String? ?: ""
    var versionFilter: (List<Blob>) -> List<Blob> = { listOf(it.last()) }
    if (options.get(Option.VERSIONS) as Boolean? == true) {
      // We want to return them in low- to high-generation order, so this will do it
      versionFilter = { it }
    }
    val blobs = buckets[bucket] ?: throw StorageException(404, "bucket $bucket does not exist")
    return Tuple.of(
      /* pageToken= */ null,
      blobs.list(prefix).flatMap(versionFilter).map { it.storageObject }
    )
  }

  override fun open(storageObject: StorageObject, options: MutableMap<Option, *>): String {
    TODO("Not yet implemented")
  }

  override fun list(options: MutableMap<Option, *>?): Tuple<String, MutableIterable<Bucket>> {
    TODO("Not yet implemented")
  }

  override fun createAcl(acl: BucketAccessControl?, options: MutableMap<Option, *>?): BucketAccessControl {
    TODO("Not yet implemented")
  }

  override fun createAcl(acl: ObjectAccessControl?): ObjectAccessControl {
    TODO("Not yet implemented")
  }

  override fun getIamPolicy(bucket: String?, options: MutableMap<Option, *>?): Policy {
    TODO("Not yet implemented")
  }

  override fun patchAcl(acl: BucketAccessControl?, options: MutableMap<Option, *>?): BucketAccessControl {
    TODO("Not yet implemented")
  }

  override fun patchAcl(acl: ObjectAccessControl?): ObjectAccessControl {
    TODO("Not yet implemented")
  }

  override fun getHmacKey(accessId: String?, options: MutableMap<Option, *>?): HmacKeyMetadata {
    TODO("Not yet implemented")
  }

  override fun getServiceAccount(projectId: String?): ServiceAccount {
    TODO("Not yet implemented")
  }

  override fun getStorage(): Storage {
    TODO("Not yet implemented")
  }

  override fun write(uploadId: String?, toWrite: ByteArray?, toWriteOffset: Int, destOffset: Long, length: Int, last: Boolean) {
    TODO("Not yet implemented")
  }

  override fun getCurrentUploadOffset(uploadId: String?): Long {
    TODO("Not yet implemented")
  }

  override fun queryCompletedResumableUpload(uploadId: String?, totalBytes: Long): StorageObject {
    TODO("Not yet implemented")
  }

  override fun writeWithResponse(uploadId: String?, toWrite: ByteArray?, toWriteOffset: Int, destOffset: Long, length: Int, last: Boolean): StorageObject {
    TODO("Not yet implemented")
  }

  override fun create(bucket: Bucket?, options: MutableMap<Option, *>?): Bucket {
    val name = bucket?.name ?: throw java.lang.UnsupportedOperationException("bucket name must be specified")
    if (buckets.exists(name)) throw StorageException(409, "bucket $bucket already exists")
    buckets.create(name)
    return Bucket().setName(name)
  }

  override fun updateHmacKey(hmacKeyMetadata: HmacKeyMetadata?, options: MutableMap<Option, *>?): HmacKeyMetadata {
    TODO("Not yet implemented")
  }

  override fun getAcl(bucket: String?, entity: String?, options: MutableMap<Option, *>?): BucketAccessControl {
    TODO("Not yet implemented")
  }

  override fun getAcl(bucket: String?, `object`: String?, generation: Long?, entity: String?): ObjectAccessControl {
    TODO("Not yet implemented")
  }

  override fun patch(bucket: Bucket?, options: MutableMap<Option, *>?): Bucket {
    TODO("Not yet implemented")
  }

  override fun deleteHmacKey(hmacKeyMetadata: HmacKeyMetadata?, options: MutableMap<Option, *>?) {
    TODO("Not yet implemented")
  }

  override fun lockRetentionPolicy(bucket: Bucket?, options: MutableMap<Option, *>?): Bucket {
    TODO("Not yet implemented")
  }

  override fun listDefaultAcls(bucket: String?): MutableList<ObjectAccessControl> {
    TODO("Not yet implemented")
  }

  override fun get(bucket: Bucket?, options: MutableMap<Option, *>?): Bucket? {
    val name = bucket?.name ?: throw java.lang.UnsupportedOperationException("bucket name must be specified")
    return if (buckets.exists(name)) Bucket().setName(name) else null
  }

  override fun testIamPermissions(bucket: String?, permissions: MutableList<String>?, options: MutableMap<Option, *>?): TestIamPermissionsResponse {
    TODO("Not yet implemented")
  }

  override fun continueRewrite(previousResponse: StorageRpc.RewriteResponse?): StorageRpc.RewriteResponse {
    TODO("Not yet implemented")
  }

  override fun createBatch(): RpcBatch {
    TODO("Not yet implemented")
  }

  override fun delete(bucket: Bucket?, options: MutableMap<Option, *>?): Boolean {
    TODO("Not yet implemented")
  }

  override fun read(from: StorageObject?, options: MutableMap<Option, *>?, position: Long, bytes: Int): Tuple<String, ByteArray> {
    TODO("Not yet implemented")
  }

  override fun read(from: StorageObject?, options: MutableMap<Option, *>?, position: Long, outputStream: OutputStream?): Long {
    TODO("Not yet implemented")
  }

  override fun createNotification(bucket: String?, notification: Notification?): Notification {
    TODO("Not yet implemented")
  }

  override fun getNotification(bucket: String?, id: String?): Notification {
    TODO("Not yet implemented")
  }

  override fun listAcls(bucket: String?, options: MutableMap<Option, *>?): MutableList<BucketAccessControl> {
    TODO("Not yet implemented")
  }

  override fun listAcls(bucket: String?, `object`: String?, generation: Long?): MutableList<ObjectAccessControl> {
    TODO("Not yet implemented")
  }

  override fun getDefaultAcl(bucket: String?, entity: String?): ObjectAccessControl {
    TODO("Not yet implemented")
  }

  override fun listHmacKeys(options: MutableMap<Option, *>?): Tuple<String, MutableIterable<HmacKeyMetadata>> {
    TODO("Not yet implemented")
  }

  override fun deleteAcl(bucket: String?, entity: String?, options: MutableMap<Option, *>?): Boolean {
    TODO("Not yet implemented")
  }

  override fun deleteAcl(bucket: String?, `object`: String?, generation: Long?, entity: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun open(signedURL: String?): String {
    TODO("Not yet implemented")
  }

  override fun compose(sources: MutableIterable<StorageObject>?, target: StorageObject?, targetOptions: MutableMap<Option, *>?): StorageObject {
    TODO("Not yet implemented")
  }

  override fun setIamPolicy(bucket: String?, policy: Policy?, options: MutableMap<Option, *>?): Policy {
    TODO("Not yet implemented")
  }

  override fun openRewrite(rewriteRequest: StorageRpc.RewriteRequest?): StorageRpc.RewriteResponse {
    TODO("Not yet implemented")
  }

  override fun deleteDefaultAcl(bucket: String?, entity: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun patchDefaultAcl(acl: ObjectAccessControl?): ObjectAccessControl {
    TODO("Not yet implemented")
  }

  override fun createDefaultAcl(acl: ObjectAccessControl?): ObjectAccessControl {
    TODO("Not yet implemented")
  }

  override fun listNotifications(bucket: String?): MutableList<Notification> {
    TODO("Not yet implemented")
  }

  override fun createHmacKey(serviceAccountEmail: String?, options: MutableMap<Option, *>?): HmacKey {
    TODO("Not yet implemented")
  }

  override fun deleteNotification(bucket: String?, notification: String?): Boolean {
    TODO("Not yet implemented")
  }
}
