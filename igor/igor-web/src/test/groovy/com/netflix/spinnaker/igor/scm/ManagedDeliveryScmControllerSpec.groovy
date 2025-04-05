/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.igor.scm

import com.netflix.spinnaker.igor.helpers.TestUtils
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.Subject

class ManagedDeliveryScmControllerSpec extends Specification {
  @Subject
  ManagedDeliveryScmController controller

  ManagedDeliveryScmService service = Mock(ManagedDeliveryScmService)

  void setup() {
    controller = new ManagedDeliveryScmController(service)
  }

  void 'list delivery config manifests returns list from service'() {
    given:
    1 * service.listDeliveryConfigManifests(scmType, project, repo, dir, extension, ref) >> expectedResponse

    when:
    List<String> response = controller.listDeliveryConfigManifests(scmType, project, repo, dir, extension, ref)

    then:
    response == expectedResponse

    where:
    scmType = 'stash'
    project = 'proj'
    repo = 'repo'
    dir = 'dir'
    extension = 'yml'
    ref = 'refs/heads/master'
    expectedResponse = [ "test.yml" ]
  }

  void 'get delivery config manifest returns contents from service'() {
    given:
    1 * service.getDeliveryConfigManifest(scmType, project, repo, dir, manifest, ref, raw) >> expectedResponse

    when:
    ResponseEntity<Map<String, Object>> response = controller.getDeliveryConfigManifest(scmType, project, repo, manifest, dir, ref, raw)

    then:
    response == new ResponseEntity(expectedResponse, HttpStatus.OK)

    where:
    scmType = 'stash'
    project = 'proj'
    repo = 'repo'
    manifest = 'manifest.yml'
    dir = 'dir'
    ref = 'refs/heads/master'
    raw = false
    expectedResponse = [
      apiVersion: "foo",
      kind: "Foo",
      metadata: [:],
      spec: [:]
    ]
  }

  void 'IllegalArgumentException from service causes a 400'() {
    given:
    1 * service.getDeliveryConfigManifest(scmType, project, repo, dir, manifest, ref, raw) >> {
      throw new IllegalArgumentException("oops!")
    }

    when:
    ResponseEntity<Map<String, Object>> response = controller.getDeliveryConfigManifest(scmType, project, repo, manifest, dir, ref, raw)

    then:
    response == new ResponseEntity<>(expectedResponse, HttpStatus.BAD_REQUEST)

    where:
    scmType = 'stash'
    project = 'proj'
    repo = 'repo'
    manifest = 'somefile'
    dir = 'dir'
    ref = 'refs/heads/master'
    raw = false
    expectedResponse = [error: "oops!"]
  }

  void '404 error from service is propagated'() {
    given:
    1 * service.getDeliveryConfigManifest(scmType, project, repo, dir, manifest, ref, raw) >> {
      throw TestUtils.makeSpinnakerHttpException("http://nada", 404, ResponseBody.create('{"detail": "oops!"}', MediaType.parse("application/json")))
    }

    when:
    ResponseEntity<Map<String, Object>> response = controller.getDeliveryConfigManifest(scmType, project, repo, manifest, dir, ref, raw)

    then:
    response == new ResponseEntity<>(expectedResponse, HttpStatus.NOT_FOUND)

    where:
    scmType = 'stash'
    project = 'proj'
    repo = 'repo'
    manifest = 'somefile'
    dir = 'dir'
    ref = 'refs/heads/master'
    raw = false
    expectedResponse = [error: [detail: "oops!"]]
  }

  void 'other exceptions from service cause a 500'() {
    given:
    1 * service.getDeliveryConfigManifest(scmType, project, repo, dir, manifest, ref, raw) >> {
      throw new RuntimeException("another oops!")
    }

    when:
    ResponseEntity<Map<String, Object>> response = controller.getDeliveryConfigManifest(scmType, project, repo, manifest, dir, ref, raw)

    then:
    response == new ResponseEntity<>(expectedResponse, HttpStatus.INTERNAL_SERVER_ERROR)

    where:
    scmType = 'stash'
    project = 'proj'
    repo = 'repo'
    manifest = 'somefile'
    dir = 'dir'
    ref = 'refs/heads/master'
    raw = false
    expectedResponse = [error: "another oops!"]
  }
}
