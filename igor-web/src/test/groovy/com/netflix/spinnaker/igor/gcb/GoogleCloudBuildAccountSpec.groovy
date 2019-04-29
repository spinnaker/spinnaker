/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.gcb

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.cloudbuild.v1.model.Build
import com.google.api.services.cloudbuild.v1.model.BuildOptions
import com.google.api.services.cloudbuild.v1.model.BuildStep
import com.google.api.services.cloudbuild.v1.model.Operation
import spock.lang.Specification

class GoogleCloudBuildAccountSpec extends Specification {
  GoogleCloudBuildClient client = Mock(GoogleCloudBuildClient)
  GoogleCloudBuildCache cache = Mock(GoogleCloudBuildCache)
  GoogleCloudBuildParser parser = new GoogleCloudBuildParser()
  GoogleCloudBuildAccount googleCloudBuildAccount = new GoogleCloudBuildAccount(client, cache, parser)

  static ObjectMapper objectMapper = new ObjectMapper()

  def "createBuild creates a build"() {
    given:
    def build = getBuild("")
    def queuedBuild = getBuild().setStatus("QUEUED").setOptions(new BuildOptions().setLogging("LEGACY"))

    when:
    def result = googleCloudBuildAccount.createBuild(build)

    then:
    1 * client.createBuild(build) >> createBuildOperation(queuedBuild)
    result == queuedBuild
  }

  def "updateBuild forwards the build to the cache"() {
    given:
    def buildId = "5ecc2461-761b-41a7-b325-210ad9b5a2b5"
    def status = "QUEUED"
    def build = getBuild(status)
    def serializedBuild = parser.serialize(build)

    when:
    googleCloudBuildAccount.updateBuild(buildId, status, serializedBuild)

    then:
    1 * cache.updateBuild(buildId, status, serializedBuild)
  }

  def "getBuild returns the value in the cache"() {
    given:
    def buildId = "5ecc2461-761b-41a7-b325-210ad9b5a2b5"
    def status = "WORKING"
    def build = getBuild(status)
    def serializedBuild = parser.serialize(build)

    when:
    def result = googleCloudBuildAccount.getBuild(buildId)

    then:
    1 * cache.getBuild(buildId) >> serializedBuild
    result == build
  }

  def "getBuild falls back to polling when nothing is in the cache, and updates the cache"() {
    given:
    def buildId = "5ecc2461-761b-41a7-b325-210ad9b5a2b5"
    def status = "WORKING"
    def build = getBuild(status)
    def serializedBuild = parser.serialize(build)

    when:
    def result = googleCloudBuildAccount.getBuild(buildId)

    then:
    1 * cache.getBuild(buildId) >> null
    1 * client.getBuild(buildId) >> build
    1 * cache.updateBuild(buildId, status, serializedBuild)
    result == build
  }

  private static Build getBuild(String status) {
    List<String> args = new ArrayList<>();
    args.add("echo");
    args.add("Hello, world!");

    BuildStep buildStep = new BuildStep().setArgs(args).setName("hello");
    BuildOptions buildOptions = new BuildOptions().setLogging("LEGACY");

    return new Build()
      .setStatus(status)
      .setSteps(Collections.singletonList(buildStep))
      .setOptions(buildOptions);
  }

  private static createBuildOperation(Build inputBuild) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("@type", "type.googleapis.com/google.devtools.cloudbuild.v1.BuildOperationMetadata");
    metadata.put("build", objectMapper.convertValue(inputBuild, Map.class));

    Operation operation = new Operation();
    operation.setName("operations/build/spinnaker-gcb-test/operationid");
    operation.setMetadata(metadata);
    return operation;
  }
}
