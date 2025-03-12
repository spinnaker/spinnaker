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

import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import org.springframework.http.HttpStatus
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls;
import spock.lang.Shared
import spock.lang.Specification


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
    Response tagsResponse =  Response.success(200, ResponseBody.create(MediaType.parse("application/json"), tagsJson))
    getTags(_,_,_,_) >> Calls.response(tagsResponse)

    String checkJson = "{}"
    Response checkResponse = Response.success(200, ResponseBody.create(MediaType.parse("application/json"), checkJson))
    checkVersion(_,_) >> Calls.response(checkResponse)

    String json = "{\"repositories\":[\"armory-io/armorycommons\",\"armory/aquascan\",\"other/keel\"]}"
    Response catalogResponse = Response.success(200, ResponseBody.create(MediaType.parse("application/json"), json))
    getCatalog(_,_,_) >> Calls.response(catalogResponse)

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
    Response schemaV2Response = Response.success(200, ResponseBody.create(MediaType.parse("application/json"), schemaJson))
    getSchemaV2Manifest(_,_,_,_) >> Calls.response(schemaV2Response)

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
    Response contentDigestResponse = Response.success(200, ResponseBody.create(MediaType.parse("application/json"), configDigestContentJson))
    getDigestContent(_,_,_,_) >> Calls.response(contentDigestResponse)
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
    1 * mockService.checkVersion(_,_) >> Calls.response(null)
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

  void "DockerRegistryClient should honor the www-authenticate header"() {
    setup:
    def authenticateDetails = "realm=\"https://auth.docker.io/token\",service=\"registry.docker.io\",scope=\"repository:${REPOSITORY1}:pull\""
    DockerBearerToken token = new DockerBearerToken()
    token.bearer_token = "bearer-token"

    when:
    client = new DockerRegistryClient("https://index.docker.io", 100, "", "", stubbedRegistryService, dockerBearerTokenService)
    client.request(() -> {throw makeSpinnakerHttpException(authenticateDetails)}, (_) -> null, REPOSITORY1)

    then:
      1 * dockerBearerTokenService.getToken(REPOSITORY1, authenticateDetails) >> token
  }
  public static SpinnakerHttpException makeSpinnakerHttpException(String authenticateDetails) {
    String url = "https://some-url";

    okhttp3.Headers headers = new okhttp3.Headers.Builder()
      .add("www-authenticate", "Bearer ${authenticateDetails}")
      .build();


    Response retrofit2Response =
      Response.error(
        ResponseBody.create(MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"),
        new okhttp3.Response.Builder()
          .code(HttpStatus.UNAUTHORIZED.value())
          .message("authentication required")
          .protocol(Protocol.HTTP_1_1)
          .request(new Request.Builder().url(url).build())
          .headers(headers)
          .build())

    Retrofit retrofit =
      new Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(JacksonConverterFactory.create())
        .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
