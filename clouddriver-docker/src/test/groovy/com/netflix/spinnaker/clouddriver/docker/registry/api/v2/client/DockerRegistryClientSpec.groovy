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

package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client

import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedInput
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/*
 * These tests all communicate with dockerhub (index.docker.io), and will either fail
 * with an exception indicating a network or HTTP error, or will fail to load data
 * from dockerhub.
 */
class DockerRegistryClientSpec extends Specification {
  private static final REPOSITORY1 = "library/ubuntu"

  @Shared
  DockerRegistryClient client
  def dockerBearerTokenService = Mock(DockerBearerTokenService)

  def stubbedRegistryService = Stub(DockerRegistryClient.DockerRegistryService){
    String tagsJson = "{\"name\":\"library/ubuntu\",\"tags\":[\"latest\",\"xenial\",\"rolling\"]}"
    TypedInput tagsTypedInput = new TypedByteArray("application/json", tagsJson.getBytes())
    Response tagsResponse = new Response("/v2/{repository}/tags/list",200, "nothing", Collections.EMPTY_LIST, tagsTypedInput)
    getTags(_,_,_) >> tagsResponse

    String checkJson = "{}"
    TypedInput checkTypedInput = new TypedByteArray("application/json", checkJson.getBytes())
    Response checkResponse = new Response("/v2/",200, "nothing", Collections.EMPTY_LIST, checkTypedInput)
    checkVersion(_,_) >> checkResponse

    String json = "{\"repositories\":[\"armory-io/armorycommons\",\"armory/aquascan\",\"other/keel\"]}"
    TypedInput catalogTypedInput = new TypedByteArray("application/json", json.getBytes())
    Response catalogResponse = new Response("/v2/_catalog/",200, "nothing", Collections.EMPTY_LIST, catalogTypedInput)
    getCatalog(_,_,_) >> catalogResponse

    String schemaJson = '''{
         "schemaVersion": 2,
         "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
         "config": {
            "mediaType": "application/vnd.docker.container.image.v1+json",
            "size": 4405,
            "digest": "sha256:fa8d22f4899110fdecf7ae344a8129fb6175ed5294ffe9ca3fb09dfca5252c93"
         },
         "layers": [
            {
               "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
               "size": 3310095,
               "digest": "sha256:1ace22715a341b6ad81b784da18f2efbcea18ff7b4b4edf4f467f193b7de3750"
            }
         ]
      }'''
    TypedInput schemaV2Input = new TypedByteArray("application/json", schemaJson.getBytes())
    Response schemaV2Response = new Response("/v2/{name}/manifests/{reference}",200, "nothing", Collections.EMPTY_LIST, schemaV2Input)
    getSchemaV2Manifest(_,_,_,_) >> schemaV2Response

    String configDigestContentJson = '''{
        "architecture": "amd64",
        "config": {
          "Hostname": "",
          "Env": [
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
          ],
          "Cmd": [
            "/opt/app/server"
          ],
          "Image": "sha256:3862e8f6f860c732be3fe0c0545330f9573a09cf906a78b06a329e09f9dc7191",
          "Volumes": null,
          "WorkingDir": "",
          "Entrypoint": null,
          "OnBuild": null,
          "Labels": {
            "branch": "main",
            "buildNumber": "1",
            "commitId": "b48e2cf960de545597411c99ec969e47a7635ba3",
            "jobName": "test"
          }
        },
        "container": "fc1607ce29cfa58cc6cad846b911ec0c4de76d426de2b528a126e715615286bc",
        "created": "2021-02-16T19:18:50.176616541Z",
        "docker_version": "19.03.6-ce",
        "os": "linux",
        "rootfs": {}
      }'''
    TypedInput configDigestContentInput = new TypedByteArray("application/json", configDigestContentJson.getBytes())
    Response contentDigestResponse = new Response("/v2/{repository}/blobs/{digest}",200, "nothing", Collections.EMPTY_LIST, configDigestContentInput)
    getDigestContent(_,_,_,_) >> contentDigestResponse
  }

  def setupSpec() {

  }

  void "DockerRegistryClient should request a real set of tags."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    def result = client.getTags(REPOSITORY1)

    then:
    result.name == REPOSITORY1
    result.tags.size() > 0
  }

  void "DockerRegistryClient should validate that it is pointing at a v2 endpoint."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    // Can only fail due to an exception thrown here.
    client.checkV2Availability()

    then:
    true
  }

  void "DockerRegistryClient invoked with insecureRegistry=true"() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    DockerRegistryTags result = client.getTags(REPOSITORY1)

    then:
    result.name == REPOSITORY1
    result.tags.size() > 0
  }

  void "DockerRegistryClient uses correct user agent"() {
    def mockService  = Mock(DockerRegistryClient.DockerRegistryService);
    client = new DockerRegistryClient("https://index.docker.io",100,"","",mockService, dockerBearerTokenService)

    when:
    client.checkV2Availability()
    def userAgent = client.userAgent

    then:
    userAgent.startsWith("Spinnaker")
    1 * mockService.checkVersion(_,_)
  }

  void "DockerRegistryClient should filter repositories by regular expression."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    def original = client.getCatalog().repositories.size()
    client = new DockerRegistryClient("https://index.docker.io",100,"","armory\\/.*",stubbedRegistryService, dockerBearerTokenService)
    def filtered = client.getCatalog().repositories.size()

    then:
    filtered < original
  }

  void "DockerRegistryClient should be able to fetch digest."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    def result = client.getConfigDigest(REPOSITORY1, "tag")

    then:
    result == "sha256:fa8d22f4899110fdecf7ae344a8129fb6175ed5294ffe9ca3fb09dfca5252c93"
  }

  void "DockerRegistryClient should be able to fetch the config layer."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    def results = client.getDigestContent(REPOSITORY1, "digest")

    then:
    results?.config?.Labels != null
    results?.config?.Labels?.commitId == "b48e2cf960de545597411c99ec969e47a7635ba3"
  }
}
