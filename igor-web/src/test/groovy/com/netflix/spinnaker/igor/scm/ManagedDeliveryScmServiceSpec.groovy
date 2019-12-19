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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.netflix.spinnaker.igor.config.ManagedDeliveryConfigProperties
import com.netflix.spinnaker.igor.scm.stash.client.StashClient
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryChild
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryChildren
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryListingResponse
import com.netflix.spinnaker.igor.scm.stash.client.model.PathDetails
import com.netflix.spinnaker.igor.scm.stash.client.model.TextLinesResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.Subject

class ManagedDeliveryScmServiceSpec extends Specification {
  @Subject
  ManagedDeliveryScmService service

  StashClient client = Mock(StashClient)
  final STASH_ADDRESS = "https://stash.com"

  ObjectMapper jsonMapper = new ObjectMapper()
  ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())

  void setup() {
      service = new ManagedDeliveryScmService(
        Optional.of(new ManagedDeliveryConfigProperties(manifestBasePath: ".spinnaker")),
        Optional.of(new StashMaster(stashClient: client, baseUrl : STASH_ADDRESS)),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        jsonMapper)
  }

  void 'list delivery config manifests'() {
    given:
    1 * client.listDirectory(project, repo, ".spinnaker/dir", ref) >> expectedResponse

    when:
    List<String> response = service.listDeliveryConfigManifests(scmType, project, repo, dir, extension, ref)

    then:
    response == expectedResponse.toChildFilenames()

    where:
    scmType = 'stash'
    project = 'proj'
    repo = 'repo'
    dir = 'dir'
    extension = 'yml'
    ref = 'refs/heads/master'
    expectedResponse = new DirectoryListingResponse(
      children: new DirectoryChildren(values: [
        new DirectoryChild(type: "FILE", path: new PathDetails(name: "test.yml"))
      ])
    )
  }

  void 'get delivery config manifest that is in yaml format'() {
    given:
    1 * client.getTextFileContents(project, repo, ".spinnaker/dir/manifest.yml", ref) >> expectedResponse

    when:
    Map<String, Object> response = service.getDeliveryConfigManifest(scmType, project, repo, dir, manifest, ref)

    then:
    response == yamlMapper.readValue(expectedResponse.toTextContents(), Map.class)

    where:
    scmType = 'stash'
    project = 'proj'
    repo = 'repo'
    manifest = 'manifest.yml'
    dir = 'dir'
    ref = 'refs/heads/master'
    expectedResponse = new TextLinesResponse(
      lines: [
        [ text: "apiVersion: foo"],
        [ text: "kind: Foo"],
        [ text: "metadata: {}"],
        [ text: "spec: {}"]
      ]
    )
  }

  void 'get delivery config manifest that is in json format'() {
    given:
    1 * client.getTextFileContents(project, repo, ".spinnaker/dir/manifest.json", ref) >> expectedResponse

    when:
    Map<String, Object> response = service.getDeliveryConfigManifest(scmType, project, repo, dir, manifest, ref)

    then:
    response == jsonMapper.readValue(expectedResponse.toTextContents(), Map.class)

    where:
    scmType = 'stash'
    project = 'proj'
    repo = 'repo'
    manifest = 'manifest.json'
    dir = 'dir'
    ref = 'refs/heads/master'
    expectedResponse = new TextLinesResponse(
      lines: [
        [ text: '{ "apiVersion": "foo", "kind": "Foo", "metadata": {}, "spec": {} }']
      ]
    )
  }
}
