/*
 * Copyright 2017 Veritas Technologies, LLC.
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

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.model.application.Application
import org.openstack4j.api.storage.ObjectStorageContainerService
import org.openstack4j.api.storage.ObjectStorageObjectService
import org.openstack4j.api.storage.ObjectStorageService
import org.openstack4j.model.storage.object.SwiftObject
import org.openstack4j.openstack.storage.object.domain.SwiftObjectImpl
import spock.lang.Shared
import spock.lang.Specification

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SwiftStorageServiceSpec extends Specification {

  @Shared
  Registry registry = new DefaultRegistry()

  @Shared
  String CONTAINER_NAME = "TestContainer"

  ObjectStorageService mockStorage = Mock(ObjectStorageService)
  SwiftStorageService swift

  SwiftStorageService makeSwift() {
    return new SwiftStorageService(CONTAINER_NAME, mockStorage)
  }

  def "ensureBucketExists creates container"() {
    given:
      ObjectStorageContainerService mockContainersApi = Mock(ObjectStorageContainerService)
      swift = makeSwift()

    when:
      swift.ensureBucketExists()

    then:
      1 * mockStorage.containers() >> mockContainersApi
      1 * mockContainersApi.list(_)
    then:
      1 * mockStorage.containers() >> mockContainersApi
      1 * mockContainersApi.create(CONTAINER_NAME, _)
  }

  def "supportsVersioning success"() {
    given:
      ObjectStorageContainerService mockContainersApi = Mock(ObjectStorageContainerService)
      Map<String, String> metadata = new HashMap<String, String>() {{ put("X-Versions-Location", "TestContainer-versions") }}
      swift = makeSwift()

    when:
      boolean res = swift.supportsVersioning()

    then:
      1 * mockStorage.containers() >> mockContainersApi
      1 * mockContainersApi.getMetadata(_) >> metadata

    then:
      res
  }

  def "supportsVersioning fails"() {
    given:
      ObjectStorageContainerService mockContainersApi = Mock(ObjectStorageContainerService)
      Map<String, String> metadata = new HashMap<String, String>() {{ put("someheader", "somecontent") }}
      swift = makeSwift()

    when:
     boolean res = swift.supportsVersioning()

    then:
      1 * mockStorage.containers() >> mockContainersApi
      1 * mockContainersApi.getMetadata(_) >> metadata

    then:
      !res
  }

  def "loadObject()"() {
    given:
      ObjectStorageObjectService mockObjectsApi = Mock(ObjectStorageObjectService)
      swift = makeSwift()
      String someKey = "testobjkey"
      SwiftObjectImpl mockObj = Mock(SwiftObjectImpl)

    when:
      swift.loadObject(ObjectType.APPLICATION, someKey)

    then:
      1 * mockStorage.objects() >> mockObjectsApi
      1 * mockObjectsApi.get(CONTAINER_NAME, someKey) >> mockObj
      1 * mockObj.getName()
      1 * mockObj.download()
  }

  def "deleteObject()"() {
    given:
      ObjectStorageObjectService mockObjectsApi = Mock(ObjectStorageObjectService)
      swift = makeSwift()
      String someKey = "testobjkey"

    when:
      swift.deleteObject(ObjectType.APPLICATION, someKey)

    then:
      1 * mockStorage.objects() >> mockObjectsApi
      1 * mockObjectsApi.delete("TestContainer", "testobjkey")
  }

  def "storeObject()"() {
    given:
      ObjectStorageObjectService mockObjectsApi = Mock(ObjectStorageObjectService)
      swift = makeSwift()
      String someKey = "testobjkey"
      Application app = new Application()

    when:
      swift.storeObject(ObjectType.APPLICATION, someKey, app)

    then:
      1 * mockStorage.objects() >> mockObjectsApi
      1 * mockObjectsApi.put("TestContainer", 'testobjkey', _, _)
  }

  def "listObjectKeys"() {
    given:
      ObjectStorageObjectService mockObjectsApi = Mock(ObjectStorageObjectService)
      swift = makeSwift()
      Map<String, String> metadata = new HashMap<String, String>() {{ put("X-Timestamp", "123456") }}

      SwiftObject obj = SwiftObjectImpl.builder()
                                       .containerName(CONTAINER_NAME)
                                       .name("testobj")
                                       .lastModified(new Date(123456))
                                       .metadata(metadata)
                                       .build()
      List<? extends SwiftObject> objs = new ArrayList<SwiftObject>() {{ add(obj) }}

    when:
      Map<String, Long> res = swift.listObjectKeys(ObjectType.APPLICATION)

    then:
      2 * mockStorage.objects() >> mockObjectsApi
      1 * mockObjectsApi.list("TestContainer", _) >> objs
      1 * mockObjectsApi.getMetadata("TestContainer", obj.getName()) >> metadata

    then:
      res.get("testobj") == Long.valueOf(123456)
  }

  def "getLastModified()"() {
    given:
      ObjectStorageObjectService mockObjectsApi = Mock(ObjectStorageObjectService)
      swift = makeSwift()
      Map<String, String> metadata = new HashMap<String, String>() {{ put("Last-Modified", "Fri, 24 Feb 2017 21:38:18 GMT") }}
      SwiftObject obj = SwiftObjectImpl.builder()
                                       .containerName(CONTAINER_NAME)
                                       .name("testobj")
                                       .lastModified(new Date())
                                       .metadata(metadata)
                                       .build()
      List<? extends SwiftObject> objs = new ArrayList<SwiftObject>() {{ add(obj) }}

    when:
      long res = swift.getLastModified(ObjectType.APPLICATION)

    then:
      2 * mockStorage.objects() >> mockObjectsApi
      1 * mockObjectsApi.list("TestContainer", _) >> objs
      1 * mockObjectsApi.getMetadata("TestContainer", obj.getName()) >> metadata

    then:
      res == ZonedDateTime.parse(metadata.get("Last-Modified"), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
  }
}
