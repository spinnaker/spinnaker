/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.orca.igor

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import retrofit.RestAdapter
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import spock.lang.Specification
import spock.lang.Subject

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor
import static com.github.tomakehurst.wiremock.client.WireMock.okJson
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.put
import static retrofit.Endpoints.newFixedEndpoint


class BuildServiceSpecMock extends Specification{

  static WireMockServer wireMockServer = new WireMockServer(0)

  @Subject BuildService buildService

  private static final MASTER = 'MASTER'
  private static final BUILD_NUMBER = 123
  private static final JOB_NAME = "name/with/slashes and spaces"
  private static final JOB_NAME_ENCODED = "name/with/slashes%20and%20spaces"
  private static final PARAMS = ['key': 'value']
  private static final FILENAME = 'build.properties'

  def setupSpec() {
    wireMockServer.start()
    configureFor(wireMockServer.port())
  }
  def mapper = new ObjectMapper()
  IgorService igorService
  def setup() {
    igorService = new RestAdapter.Builder()
          .setEndpoint(newFixedEndpoint(wireMockServer.url("/")))
          .setClient(new Ok3Client())
          .setConverter(new JacksonConverter(mapper))
          .build()
          .create(IgorService)
    buildService = new BuildService(igorService, new IgorFeatureFlagProperties(jobNameAsQueryParameter: false))
  }

  def cleanupSpec() {
    wireMockServer.stop()
  }

  def "build starts a Jenkins job"() {
    String queryMap = PARAMS.collect { k, v -> "$k=$v" }.join('&')
    String uriPath = "/masters/$MASTER/jobs/$JOB_NAME_ENCODED?$queryMap"
    stubFor(put(uriPath)
        .willReturn(
            aResponse()
                .withStatus(200)
                .withBody(BUILD_NUMBER.toString())
        )
    )

    when:
    Response response = buildService.build(MASTER, JOB_NAME, PARAMS)

    then:
    response.status == 200
  }



  def "getBuild returns the build details - jobNameAsQueryParameter:false"() {
    given:
    String uriPath = "/builds/status/$BUILD_NUMBER/$MASTER/$JOB_NAME_ENCODED"
    stubFor(get(urlEqualTo(uriPath))
        .willReturn(okJson(getBuildResponse())))

    when:
    Map<String, Object> response = buildService.getBuild(BUILD_NUMBER,MASTER,JOB_NAME)
    then:
    response.get("name") == JOB_NAME
  }

  def "getBuild returns the build details - jobNameAsQueryParameter:true"() {
    given:
    String uriPath = "/builds/status/$BUILD_NUMBER/$MASTER?job=$JOB_NAME_ENCODED"
    IgorFeatureFlagProperties igorFeatureFlagProperties = new IgorFeatureFlagProperties()
    igorFeatureFlagProperties.setJobNameAsQueryParameter(true)
    buildService = new BuildService(igorService, igorFeatureFlagProperties)
    stubFor(get(urlEqualTo(uriPath))
        .willReturn(okJson(getBuildResponse())))

    when:
    Map<String, Object> response = buildService.getBuild(BUILD_NUMBER,MASTER,JOB_NAME)
    then:
    response.get("name") == JOB_NAME
  }

  def "getPropertyFile returns a property file - jobNameAsQueryParameter:false"() {
    given:
    String uriPath = "/builds/properties/$BUILD_NUMBER/$FILENAME/$MASTER/$JOB_NAME_ENCODED"
    stubFor(get(urlEqualTo(uriPath))
        .willReturn(
            okJson(getBuildPropertiesResponse())
        )
    )

    when:
    Map<String, Object> response = buildService.getPropertyFile(BUILD_NUMBER, FILENAME, MASTER, JOB_NAME)

    then:
    response.get("randomKey") == "randomValue"
  }

  def "getPropertyFile returns a property file - jobNameAsQueryParameter:true"() {
    given:
    String uriPath = "/builds/properties/$BUILD_NUMBER/$FILENAME/$MASTER?job=$JOB_NAME_ENCODED"
    IgorFeatureFlagProperties igorFeatureFlagProperties = new IgorFeatureFlagProperties()
    igorFeatureFlagProperties.setJobNameAsQueryParameter(true)
    buildService = new BuildService(igorService, igorFeatureFlagProperties)
    stubFor(get(urlEqualTo(uriPath))
        .willReturn(
            okJson(getBuildPropertiesResponse())
        )
    )

    when:
    Map<String, Object> response = buildService.getPropertyFile(BUILD_NUMBER, FILENAME, MASTER, JOB_NAME)

    then:
    response.get("randomKey") == "randomValue"

  }

  def "getArtifacts returns build artifacts - jobNameAsQueryParameter:false"() {
    given:
    String uriPath = "/builds/artifacts/$BUILD_NUMBER/$MASTER/$JOB_NAME_ENCODED?propertyFile=$FILENAME"
    stubFor(get(urlEqualTo(uriPath))
        .willReturn(
            okJson(getArtifactsResponse())
        )
    )

    when:
    List<Artifact> response = buildService.getArtifacts(BUILD_NUMBER, FILENAME, MASTER, JOB_NAME)

    then:
    response.toString() == "[]"
  }

  def "getArtifacts returns build artifacts - jobNameAsQueryParameter:true"() {
    given:
    String uriPath = "/builds/artifacts/$BUILD_NUMBER/$MASTER?propertyFile=$FILENAME&job=$JOB_NAME_ENCODED"
    IgorFeatureFlagProperties igorFeatureFlagProperties = new IgorFeatureFlagProperties()
    igorFeatureFlagProperties.setJobNameAsQueryParameter(true)
    buildService = new BuildService(igorService, igorFeatureFlagProperties)
    stubFor(get(urlEqualTo(uriPath))
        .willReturn(
            okJson(getArtifactsResponse())
        )
    )

    when:
    List<Artifact> response = buildService.getArtifacts(BUILD_NUMBER, FILENAME, MASTER, JOB_NAME)

    then:
    response.toString() == "[]"
  }


  private static String getBuildResponse() {
    return "{\n" +
        "  \"building\": false,\n" +
        "  \"fullDisplayName\": \"$JOB_NAME #$BUILD_NUMBER\",\n" +
        "  \"name\": \"$JOB_NAME\",\n" +
        "  \"number\": $BUILD_NUMBER,\n" +
        "  \"duration\": 778,\n" +
        "  \"timestamp\": \"1714730108196\",\n" +
        "  \"result\": \"SUCCESS\",\n" +
        "  \"artifacts\": [\n" +
        "    {\n" +
        "      \"fileName\": \"$FILENAME\",\n" +
        "      \"displayPath\": \"$FILENAME\",\n" +
        "      \"relativePath\": \"$FILENAME\",\n" +
        "      \"reference\": \"$FILENAME\",\n" +
        "      \"name\": \"$JOB_NAME\",\n" +
        "      \"type\": \"jenkins/file\",\n" +
        "      \"version\": \"6\",\n" +
        "      \"decorated\": false\n" +
        "    }\n" +
        "  ],\n" +
        "  \"testResults\": [\n" +
        "    {\n" +
        "      \"failCount\": 0,\n" +
        "      \"skipCount\": 0,\n" +
        "      \"totalCount\": 0,\n" +
        "      \"urlName\": null,\n" +
        "      \"cause\": {\n" +
        "        \"shortDescription\": \"Started by user Spinnaker\",\n" +
        "        \"upstreamBuild\": null,\n" +
        "        \"upstreamProject\": null,\n" +
        "        \"upstreamUrl\": null\n" +
        "      }\n" +
        "    }\n" +
        "  ],\n" +
        "  \"url\": \"https://jenkins.somedomain.com/job/$JOB_NAME_ENCODED/$BUILD_NUMBER/\"\n" +
        "}"
  }

  private static String getBuildPropertiesResponse() {
    return "{\"randomKey\":\"randomValue\"}"
  }

  private static String getArtifactsResponse() {
    return "[]"
  }


}
