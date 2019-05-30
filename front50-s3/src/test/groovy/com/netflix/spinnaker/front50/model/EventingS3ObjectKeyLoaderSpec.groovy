/*
 * Copyright 2017 Netflix, Inc.
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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.config.S3Properties
import com.netflix.spinnaker.front50.model.events.S3Event
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.text.SimpleDateFormat
import java.util.concurrent.ExecutorService

class EventingS3ObjectKeyLoaderSpec extends Specification {
  def taskScheduler = Mock(ExecutorService)
  def objectMapper = new ObjectMapper()
  def s3Properties = new S3Properties(
    rootFolder: "root"
  )
  def temporarySQSQueue = Mock(TemporarySQSQueue)
  def s3StorageService = Mock(S3StorageService)
  def registry = Mock(Registry)

  @Subject
  def objectKeyLoader = new EventingS3ObjectKeyLoader(
    taskScheduler,
    objectMapper,
    s3Properties,
    temporarySQSQueue,
    s3StorageService,
    registry,
    false
  )

  @Unroll
  def "should build object key"() {
    when:
    def keyWithObjectType = EventingS3ObjectKeyLoader.buildObjectKey(rootFolder, s3ObjectKey)

    then:
    keyWithObjectType == new EventingS3ObjectKeyLoader.KeyWithObjectType(expectedObjectType, expectedKey)

    where:
    rootFolder | s3ObjectKey                                                              || expectedKey                   || expectedObjectType
    "my/root/" | "my/root/tags/aws%3Aservergroup%3Amy_asg-v720/entity-tags-metadata.json" || "aws:servergroup:my_asg-v720" || ObjectType.ENTITY_TAGS // should decode s3ObjectKey
    "my/root"  | "my/root/tags/aws%3Aservergroup%3Amy_asg-v720/entity-tags-metadata.json" || "aws:servergroup:my_asg-v720" || ObjectType.ENTITY_TAGS // trailing slash is optional
    "my/root/" | "my/root/applications/my_application/application-metadata.json"          || "my_application"              || ObjectType.APPLICATION
  }

  def "should apply recent modifications when listing object keys"() {
    given:
    objectKeyLoader.objectKeysByLastModifiedCache.putAll([
      (new EventingS3ObjectKeyLoader.KeyWithObjectType(ObjectType.APPLICATION, "key1")): 95L,
      (new EventingS3ObjectKeyLoader.KeyWithObjectType(ObjectType.PIPELINE, "key2"))   : 205L,
      (new EventingS3ObjectKeyLoader.KeyWithObjectType(ObjectType.APPLICATION, "key3")): 210L,
    ])

    when:
    def objectKeys = objectKeyLoader.listObjectKeys(ObjectType.APPLICATION)

    then:
    1 * s3StorageService.supportsEventing(_) >> { return true }
    1 * s3StorageService.listObjectKeys(ObjectType.APPLICATION) >> {
      return [
        "key1": 100L,
        "key2": 105L,
        "key3": 110L
      ]
    }

    objectKeys == [
      "key1": 100L,    // recent modification isn't actually newer
      "key2": 105L,    // pipeline:key2 != application:key2
      "key3": 210L     // recent modification should override
    ]
  }

  def "should record all modifications contained within an S3Event"() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
    given:
    def s3Event = new S3Event(
      records: [
        new S3Event.S3EventRecord(
          eventName: "PUT",
          eventTime: sdf.format(new Date(9999)),
          s3: new S3Event.S3Meta(object: new S3Event.S3Object(key: "root/applications/last-modified.json")) // last-modified.json keys should be skipped
        ),
        new S3Event.S3EventRecord(
          eventName: "PUT",
          eventTime: sdf.format(new Date(5000)),
          s3: new S3Event.S3Meta(object: new S3Event.S3Object(key: "root/applications/key1/application-metadata.json"))
        ),
        new S3Event.S3EventRecord(
          eventName: "PUT",
          eventTime: sdf.format(new Date(25000)),
          s3: new S3Event.S3Meta(object: new S3Event.S3Object(key: "root/pipelines/key2/pipeline-metadata.json"))
        )
      ]
    )

    when:
    objectKeyLoader.tick(s3Event)

    then:
    objectKeyLoader.objectKeysByLastModifiedCache.asMap() == [
      (new EventingS3ObjectKeyLoader.KeyWithObjectType(ObjectType.APPLICATION, "key1")): 5000L,
      (new EventingS3ObjectKeyLoader.KeyWithObjectType(ObjectType.PIPELINE, "key2"))   : 25000L
    ]
  }
}
