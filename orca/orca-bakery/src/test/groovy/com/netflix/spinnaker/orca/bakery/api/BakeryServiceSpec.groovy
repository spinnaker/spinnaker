/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.api

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.RequestListener
import com.github.tomakehurst.wiremock.http.Response
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.config.DefaultServiceClientProvider
import com.netflix.spinnaker.orca.bakery.api.manifests.kustomize.KustomizeBakeManifestRequest
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.bakery.tasks.manifests.BakeManifestContext
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.google.common.net.HttpHeaders.LOCATION
import static java.net.HttpURLConnection.*

import com.netflix.spinnaker.orca.test.Retrofit2TestConfig

@SpringBootTest(
    classes = [Retrofit2TestConfig],
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BakeryServiceSpec extends Specification {

  static WireMockServer wireMockServer = new WireMockServer(0)

  @Subject
  BakeryService bakery
  @Subject
  BlockingVariable<String> actualUrl
  @Subject
  BlockingVariable<String> actualPayload

  @Autowired
  ApplicationContext applicationContext

  @Autowired
  DefaultServiceClientProvider serviceClientProvider

  private static final region = "us-west-1"
  private static final bake = BakeRequest.Default.copyWith(user: "rfletcher", packageName: "orca")
  private static final bakePath = "/api/v1/$region/bake"
  private static final statusPath = "/api/v1/$region/status"
  private static final bakeId = "b-123456789"
  private static final statusId = "s-123456789"

  static String bakeURI
  static String statusURI

  def mapper = OrcaObjectMapper.newInstance()

  def setup() {
    actualUrl = new BlockingVariable<String>(5)
    actualPayload = new BlockingVariable<String>(5)
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.addMockServiceRequestListener(new RequestListener() {

      @Override
      void requestReceived(Request request, Response response) {
        actualUrl.set(request.absoluteUrl)
        actualPayload.set(request.bodyAsString)
      }
    })
    wireMockServer.start()
    configureFor(wireMockServer.port())
    bakeURI = wireMockServer.url(bakePath)
    statusURI = wireMockServer.url(statusPath)

    bakery = new BakeryConfiguration()
      .buildService(wireMockServer.url("/"), serviceClientProvider)
  }

  def cleanupSpec() {
    wireMockServer.stop()
  }

  def "should receive specific BakeManifestRequest when baking a manifest"() {
    given:
    def type = "KUSTOMIZE"
    def bakeManifestPath = "/api/v2/manifest/bake/$type"
    def inputArtifactPassed = Artifact.builder().name("my-manifest").type("embedded/base64").reference("ZGF0YQ==").build()
    def outputArtifactNamePassed = "baked-manifest"
    def stageJson = """
          {
            "expectedArtifacts": [
              {
                "defaultArtifact": {
                  "customKind": true,
                  "id": "bd95dd08-58a3-4012-9db5-4c4cde176e0b"
                },
                "displayName": "rare-gecko-67",
                "id": "ea011068-f42e-4df0-8cf0-2fad1a6fc47b",
                "matchArtifact": {
                  "artifactAccount": "embedded-artifact",
                  "id": "86c1ef35-0b8a-4892-a60a-82759d8aa6ab",
                  "name": "hi",
                  "type": "embedded/base64"
                },
                "useDefaultArtifact": false,
                "usePriorArtifact": false
              }
            ],
            "inputArtifacts": [
              {
                "account": "no-auth-http-account",
                "artifact": {
                  "artifactAccount": "no-auth-http-account",
                  "id": "c4d18108-2b3b-40b1-ba82-d22ce17e708b",
                  "reference": "kustomizefile.yml",
                  "type": "http/file"
                },
                "id": null
              }
            ],
            "isNew": true,
            "name": "BakeManifest",
            "outputName": "resolvedartifact",
            "kustomizeFilePath": "kustomizefile.yml",
            "type": "createBakeManifest",
            "environment": "prod",
            "includeCRDs": "true",
            "templateRenderer": "KUSTOMIZE",
            "namespace": "test"
          }
          """

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    BakeManifestContext context = stage.mapTo(BakeManifestContext.class);
    def kustomizeBakeManifestRequest = new KustomizeBakeManifestRequest(context, inputArtifactPassed, outputArtifactNamePassed);
    stubFor(
        post(bakeManifestPath)
            .willReturn(
                aResponse()
                    .withStatus(HTTP_OK)
                    .withBody(mapper.writeValueAsString(inputArtifactPassed))
            )
    )

    when:
    Retrofit2SyncCall.execute(bakery.bakeManifest(type, kustomizeBakeManifestRequest))
    def actualRequest = mapper.readValue(actualPayload.get(), Map)

    then:
    with(actualRequest) {
      //FIXME: inputArtifact is not supposed to be null
      inputArtifact == null
      outputName == context.outputName
      outputArtifactName == outputArtifactNamePassed
      templateRenderer == "KUSTOMIZE"
    }
  }

  def "can lookup a bake status"() {
    given:
    stubFor(
      get("$statusPath/$statusId")
        .willReturn(
        aResponse()
          .withStatus(HTTP_OK)
          .withBody(mapper.writeValueAsString([
          state       : "COMPLETED",
          progress    : 100,
          status      : "SUCCESS",
          code        : 0,
          resource_id : bakeId,
          resource_uri: "$bakeURI/$bakeId",
          uri         : "$statusURI/$statusId",
          id          : statusId,
          attempts    : 0,
          ctime       : 1382310109766,
          mtime       : 1382310294223,
          messages    : ["amination success"]
        ]))
      )
    )

    expect:
    with(Retrofit2SyncCall.execute(bakery.lookupStatus(region, statusId))) {
      id == statusId
      state == BakeStatus.State.COMPLETED
      resourceId == bakeId
    }
  }

  def "looking up an unknown status id will throw an exception"() {
    given:
    stubFor(
      get("$statusPath/$statusId")
        .willReturn(
        aResponse()
          .withStatus(HTTP_NOT_FOUND)
          .withBody("{\"message\": \"error\"}")
      )
    )

    when:
    Retrofit2SyncCall.execute(bakery.lookupStatus(region, statusId))

    then:
    def ex = thrown(SpinnakerHttpException)
    ex.responseCode == HTTP_NOT_FOUND
  }

  def "should return status of newly created bake"() {
    given: "the bakery accepts a new bake"
    stubFor(
      post(bakePath)
        .willReturn(
        aResponse()
          .withStatus(HTTP_ACCEPTED)
          .withBody(mapper.writeValueAsString([
          state       : "PENDING",
          progress    : 0,
          resource_id : bakeId,
          resource_uri: "$bakeURI/$bakeId",
          uri         : "$statusURI/$statusId",
          id          : statusId,
          attempts    : 0,
          ctime       : 1382310109766,
          mtime       : 1382310109766,
          messages    : []
        ]))
      )
    )

    expect: "createBake should return the status of the bake"
    with(Retrofit2SyncCall.execute(bakery.createBake(region, bake, null))) {
      id == statusId
      state == BakeStatus.State.PENDING
      resourceId == bakeId
    }
  }

  def "should handle a repeat create bake response"() {
    given: "the POST to /bake redirects to the status of an existing bake"
    stubFor(
      post(bakePath)
        .willReturn(
        aResponse()
          .withStatus(HTTP_SEE_OTHER)
          .withHeader(LOCATION, "$statusURI/$statusId")
      )
    )
    stubFor(
      get("$statusPath/$statusId")
        .willReturn(
        aResponse()
          .withStatus(HTTP_OK)
          .withBody(mapper.writeValueAsString([
          state       : "RUNNING",
          progress    : 1,
          resource_id : bakeId,
          resource_uri: "$bakeURI/$bakeId",
          uri         : "$statusURI/$statusId",
          id          : statusId,
          attempts    : 1,
          ctime       : 1382310109766,
          mtime       : 1382310109766,
          messages    : ["on instance i-66f5913d runnning: aminate ..."]
        ])
        )
      )
    )

    expect: "createBake should return the status of the bake"
    with(Retrofit2SyncCall.execute(bakery.createBake(region, bake, null))) {
      id == statusId
      state == BakeStatus.State.RUNNING
      resourceId == bakeId
      // TODO: would we actually get a bake id if it was incomplete?
    }
  }

  def "can lookup the details of a bake"() {
    given:
    stubFor(
      get("$bakePath/$bakeId")
        .willReturn(
        aResponse()
          .withStatus(HTTP_OK)
          .withBody(mapper.writeValueAsString([
          ami       : "ami",
          base_ami  : "base_ami",
          ami_suffix: "ami_suffix",
          base_name : "base_name",
          ami_name  : "ami_name",
          id        : bakeId
        ]))
      )
    )

    expect:
    with(Retrofit2SyncCall.execute(bakery.lookupBake(region, bakeId))) {
      id == bakeId
      ami == "ami"
    }
  }
}
