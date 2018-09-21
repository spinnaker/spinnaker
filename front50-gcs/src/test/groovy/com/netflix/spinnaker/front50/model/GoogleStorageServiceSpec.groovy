/*
 * Copyright 2016 Google, Inc.
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

import com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting
import com.google.api.client.testing.json.MockJsonFactory
import com.netflix.spinnaker.front50.model.application.Application;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.DefaultRegistry;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpHeaders
import com.google.api.client.util.DateTime;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.*;
import com.google.api.client.http.HttpResponseException
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.TaskScheduler;
import spock.lang.Shared
import spock.lang.Specification

class GoogleStorageServiceSpec extends Specification {

  @Shared
  Registry registry = new DefaultRegistry()

  @Shared
  String BUCKET_NAME = "TestBucket"

  @Shared
  String BUCKET_LOCATION = "US"

  @Shared
  String BASE_PATH = "/A/B/C"

  @Shared
  String PROJECT_NAME = "TestProject"

  Storage mockStorage = Mock(Storage)
  Storage.Objects mockObjectApi = Mock(Storage.Objects)
  GcsStorageService gcs
  TaskScheduler mockScheduler = Mock(TaskScheduler)

  GcsStorageService makeGcs() {
    return makeGcs(-1)
  }

  GcsStorageService makeGcs(int maxRetries) {
    return new GcsStorageService(BUCKET_NAME, BUCKET_LOCATION, BASE_PATH, PROJECT_NAME,
                                 mockStorage, maxRetries, mockScheduler, registry)
  }

  def "ensureBucketExists make bucket"() {
    given:
     Storage.Buckets mockBucketApi = Mock(Storage.Buckets)
     Storage.Buckets.Get mockGetBucket = Mock(Storage.Buckets.Get)
     Storage.Buckets.Insert mockInsertBucket = Mock(Storage.Buckets.Insert)
     Bucket.Versioning ver = new Bucket.Versioning().setEnabled(true)
     Bucket bucketSpec = new Bucket()
         .setName(BUCKET_NAME)
         .setVersioning(ver)
         .setLocation(BUCKET_LOCATION)

    when:
     gcs = makeGcs()
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
      gcs.ensureBucketExists()

    then:
      1 * mockStorage.buckets() >> mockBucketApi
      1 * mockBucketApi.get(BUCKET_NAME) >> mockGetBucket
      1 * mockGetBucket.execute() >> {
        throw new HttpResponseException.Builder(404, 'Oops', new HttpHeaders()).build()
      }

    then:
      1 * mockStorage.buckets() >> mockBucketApi
      1 * mockBucketApi.insert(PROJECT_NAME, bucketSpec) >> mockInsertBucket
      1 * mockInsertBucket.execute()
  }

  def "ensureBucketExists exists"() {
    given:
     Storage.Buckets mockBucketApi = Mock(Storage.Buckets)
     Storage.Buckets.Get mockGetBucket = Mock(Storage.Buckets.Get)

    when:
     gcs = makeGcs()
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
      gcs.ensureBucketExists()

    then:
      1 * mockStorage.buckets() >> mockBucketApi
      1 * mockBucketApi.get(BUCKET_NAME) >> mockGetBucket
      1 * mockGetBucket.execute() >> new Bucket()
  }

  def "ensureBucketExists fails"() {
    given:
     Storage.Buckets mockBucketApi = Mock(Storage.Buckets)
     Storage.Buckets.Get mockGetBucket = Mock(Storage.Buckets.Get)
     HttpResponseException exception = new HttpResponseException.Builder(
         403, 'Oops', new HttpHeaders()).build()

    when:
     gcs = makeGcs()
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
      gcs.ensureBucketExists()

    then:
      1 * mockStorage.buckets() >> mockBucketApi
      1 * mockBucketApi.get(BUCKET_NAME) >> mockGetBucket
      1 * mockGetBucket.execute() >> {
        throw exception
      }
      thrown(IllegalStateException)
  }

  def "loadObject"() {
    given:
     String jsonPath = BASE_PATH + '/applications/testKey/specification.json'
     Storage.Objects.Get mockGetFile = Mock(Storage.Objects.Get)

     // Normally the object will be consistent with the name/bucket of its
     // location. However the actual implementation doesnt enforce that.
     // The name is normally the path to the object.
     StorageObject storageObject = new StorageObject()
                                           .setBucket("TheStorageBucket")
                                           .setName("TheStorageObject")
                                           .setUpdated(new DateTime(1234567))

     byte[] json = """{
          "name":"TESTAPPLICATION",
          "description":"A Test Application",
          "email":"user@email.com",
          "accounts":"my-account",
          "updateTs":"1464227414608",
          "createTs":"1464227413146",
          "platformHealthOnly":true,
          "cloudProviders":"platformA, platformB"
     }""".getBytes()

    when:
     gcs = makeGcs()
    then:
     1 * mockStorage.objects() >> mockObjectApi

     when:
      Application app = gcs.loadObject(ObjectType.APPLICATION, "testKey")
     then:
      1 * mockObjectApi.get(BUCKET_NAME, jsonPath) >> mockGetFile
      1 * mockGetFile.execute() >> storageObject

      then:
      1 * mockObjectApi.get(storageObject.getBucket(),
                            storageObject.getName()) >> mockGetFile
      1 * mockGetFile.executeMediaAndDownloadTo(_) >> {
          ByteArrayOutputStream output -> output.write(json, 0, json.size())
      }

      then:
        app.getName() == "TESTAPPLICATION"
        app.getLastModified() == storageObject.getUpdated().getValue()

  }

  def "deleteObject"() {
    given:
     Storage.Objects.Delete mockDeleteObject = Mock(Storage.Objects.Delete)
     Storage.Objects.Patch mockUpdateObject = Mock(Storage.Objects.Patch)
     StorageObject storageObject = new StorageObject()
       .setBucket(BUCKET_NAME)
       .setName(BASE_PATH + '/applications/deletekey/specification.json')

     StorageObject timestampObject = new StorageObject()
       .setBucket(BUCKET_NAME)
       .setName(BASE_PATH + '/applications/last-modified')
     long startTime = System.currentTimeMillis()

    when:
     gcs = makeGcs()
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
     gcs.deleteObject(ObjectType.APPLICATION, "deletekey")

    then:
     1 * mockObjectApi.delete(
       storageObject.getBucket(), storageObject.getName()) >> mockDeleteObject
     1 * mockDeleteObject.execute()

    then:
     1 * mockObjectApi.patch(
       BUCKET_NAME,
       timestampObject.getName(),
       { StorageObject obj ->
         obj.getBucket() == timestampObject.getBucket()
         obj.getName() == timestampObject.getName()
         obj.getUpdated().getValue() >= startTime
         obj.getUpdated().getValue() <= System.currentTimeMillis()
       }
     ) >> mockUpdateObject

     1 * mockUpdateObject.execute()
     gcs.purgeOldVersionPaths.toArray() == [timestampObject.getName()]
  }

  def "deleteObjectNotFound"() {
    given:
     Storage.Objects.Delete mockDeleteObject = Mock(Storage.Objects.Delete)
     Storage.Objects.Patch mockUpdateObject = Mock(Storage.Objects.Patch)
     StorageObject storageObject = new StorageObject()
       .setBucket(BUCKET_NAME)
       .setName(BASE_PATH + '/applications/deletekey/specification.json')

     StorageObject timestampObject = new StorageObject()
       .setBucket(BUCKET_NAME)
       .setName(BASE_PATH + '/applications/last-modified')
     long startTime = System.currentTimeMillis()

    when:
     gcs = makeGcs(10)
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
     gcs.deleteObject(ObjectType.APPLICATION, "deletekey")

    then:
     1 * mockObjectApi.delete(
       storageObject.getBucket(), storageObject.getName()) >> mockDeleteObject
     1 * mockDeleteObject.execute() >> {
       throw GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found")
     }

    then:
     1 * mockObjectApi.patch(
       BUCKET_NAME,
       timestampObject.getName(),
       { StorageObject obj ->
         obj.getBucket() == timestampObject.getBucket()
         obj.getName() == timestampObject.getName()
         obj.getUpdated().getValue() >= startTime
         obj.getUpdated().getValue() <= System.currentTimeMillis()
       }
     ) >> mockUpdateObject

     1 * mockUpdateObject.execute()
     gcs.purgeOldVersionPaths.toArray() == [timestampObject.getName()]
  }

  def "deleteObjectWithRetry"() {
    given:
     Storage.Objects.Delete mockDeleteObject = Mock(Storage.Objects.Delete)
     Storage.Objects.Patch mockUpdateObject = Mock(Storage.Objects.Patch)
     StorageObject storageObject = new StorageObject()
       .setBucket(BUCKET_NAME)
       .setName(BASE_PATH + '/applications/deletekey/specification.json')

     StorageObject timestampObject = new StorageObject()
       .setBucket(BUCKET_NAME)
       .setName(BASE_PATH + '/applications/last-modified')
     long startTime = System.currentTimeMillis()

    when:
     gcs = makeGcs(10)
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
     gcs.deleteObject(ObjectType.APPLICATION, "deletekey")

    then:
     2 * mockObjectApi.delete(
       storageObject.getBucket(), storageObject.getName()) >> mockDeleteObject
     1 * mockDeleteObject.execute() >> {
       throw new SocketTimeoutException()
     }
     1 * mockDeleteObject.execute()

    then:
     1 * mockObjectApi.patch(
       BUCKET_NAME,
       timestampObject.getName(),
       { StorageObject obj ->
         obj.getBucket() == timestampObject.getBucket()
         obj.getName() == timestampObject.getName()
         obj.getUpdated().getValue() >= startTime
         obj.getUpdated().getValue() <= System.currentTimeMillis()
       }
     ) >> mockUpdateObject

     1 * mockUpdateObject.execute()
     gcs.purgeOldVersionPaths.toArray() == [timestampObject.getName()]
  }

  def "storeObject"() {
    given:
     Storage.Objects.Patch mockUpdateObject = Mock(Storage.Objects.Patch)
     Storage.Objects.Insert mockInsertObject = Mock(Storage.Objects.Insert)

     StorageObject storageObject = new StorageObject()
       .setBucket(BUCKET_NAME)
       .setName(BASE_PATH + '/applications/testkey/specification.json')

     StorageObject timestampObject = new StorageObject()
       .setBucket(BUCKET_NAME)
       .setName(BASE_PATH + '/applications/last-modified')
     Application app = new Application()
     byte[] appBytes = new ObjectMapper().writeValueAsBytes(app)
     ByteArrayContent appContent = new ByteArrayContent(
       "application/json", appBytes)
     ByteArrayContent timestampContent = new ByteArrayContent(
       "application/json", "{}".getBytes())
     long startTime = System.currentTimeMillis()

    when:
     gcs = makeGcs()
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
     gcs.storeObject(ObjectType.APPLICATION, "testkey", app)

    then:
     1 * mockObjectApi.insert(
       BUCKET_NAME,
       { StorageObject obj ->
         obj.getBucket() == storageObject.getBucket()
         obj.getName() == storageObject.getName()
       },
       _) >> mockInsertObject
     1 * mockInsertObject.execute()

    then:
     1 * mockObjectApi.patch(
       BUCKET_NAME,
       timestampObject.getName(),
       { StorageObject obj ->
         obj.getBucket() == timestampObject.getBucket()
         obj.getName() == timestampObject.getName()
         obj.getUpdated().getValue() >= startTime
         obj.getUpdated().getValue() <= System.currentTimeMillis()
       }
     ) >> mockUpdateObject

     1 * mockUpdateObject.execute()
     gcs.purgeOldVersionPaths.toArray() == [timestampObject.getName()]
  }

  def "purgeOldVersions" () {
    given:
     Storage.Objects.List mockListObjects = Mock(Storage.Objects.List)
     String timestamp_path = BASE_PATH + '/applications/last-modified'
     com.google.api.services.storage.model.Objects objects = new com.google.api.services.storage.model.Objects()

    when:
     gcs = makeGcs()
     gcs.purgeOldVersionPaths.add(timestamp_path)

    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
     gcs.purgeBatchedVersionPaths()

    then:
     1 * mockObjectApi.list(BUCKET_NAME) >> mockListObjects
     1 * mockListObjects.setPrefix(timestamp_path) >> mockListObjects
     1 * mockListObjects.setVersions(true) >> mockListObjects
     1 * mockListObjects.execute() >> objects
     1 * mockListObjects.setPageToken(_)
  }

  def "rateLimitExceeded"() {
    given:
      Storage.Objects.Patch mockUpdateObject = Mock(Storage.Objects.Patch)
      Storage.Objects.Insert mockInsertObject = Mock(Storage.Objects.Insert)
      Storage.Objects.List mockListObjects = Mock(Storage.Objects.List)
      com.google.api.services.storage.model.Objects objects = new com.google.api.services.storage.model.Objects()
      StorageObject storageObject = new StorageObject()
              .setBucket(BUCKET_NAME)
              .setName(BASE_PATH + '/applications/testkey/specification.json')

      StorageObject timestampObject = new StorageObject()
        .setBucket(BUCKET_NAME)
        .setName(BASE_PATH + '/applications/last-modified')
      Application app = new Application()
      byte[] appBytes = new ObjectMapper().writeValueAsBytes(app)
      ByteArrayContent appContent = new ByteArrayContent(
        "application/json", appBytes)
      ByteArrayContent timestampContent = new ByteArrayContent(
        "application/json", "{}".getBytes())
      long startTime = System.currentTimeMillis()

    when:
     gcs = makeGcs()
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
      gcs.storeObject(ObjectType.APPLICATION, "testkey", app)

    then:
      1 * mockObjectApi.insert(
           BUCKET_NAME,
           { StorageObject obj -> obj.getBucket() == storageObject.getBucket()
                                  obj.getName() == storageObject.getName() },
           _) >> mockInsertObject
      1 * mockInsertObject.execute()

    then:
      1 * mockObjectApi.patch(
              BUCKET_NAME,
              timestampObject.getName(),
              { StorageObject obj -> obj.getBucket() == timestampObject.getBucket()
                obj.getName() == timestampObject.getName()
                obj.getUpdated().getValue() >= startTime
                obj.getUpdated().getValue() <= System.currentTimeMillis()
              }
              ) >> mockUpdateObject

      1 * mockUpdateObject.execute() >> {
        throw new HttpResponseException.Builder(503, 'Exceeded API quota', new HttpHeaders()).build()
      }

     then:
      1 * mockScheduler.schedule(_, _);
  }

  def "scheduleOncePerType"() {
    when:
     gcs = makeGcs()
    then:
     1 * mockStorage.objects() >> mockObjectApi

    when:
      gcs.scheduleWriteLastModified('TypeOne')
    then:
      1 * mockScheduler.schedule(_, _);

    when:
      gcs.scheduleWriteLastModified('TypeOne')
    then:
      0 * mockScheduler.schedule(_, _);

    when:
      gcs.scheduleWriteLastModified('TypeTwo')
    then:
      1 * mockScheduler.schedule(_, _);
  }
}
