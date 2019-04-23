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
import spock.lang.Specification
import spock.lang.Subject;

class GoogleCloudBuildParserSpec extends Specification {
  @Subject
  GoogleCloudBuildParser parser = new GoogleCloudBuildParser()

  ObjectMapper objectMapper = new ObjectMapper();

  def "correctly parses a build"() {
    given:
    def build = getBuild()
    def serializedBuild = objectMapper.writeValueAsString(build)
    def deserializedBuild = parser.parse(serializedBuild, Build.class)

    expect:
    deserializedBuild == build
  }

  private static Build getBuild() {
    List<String> args = new ArrayList<>();
    args.add("echo");
    args.add("Hello, world!");

    BuildStep buildStep = new BuildStep().setArgs(args).setName("hello");
    BuildOptions buildOptions = new BuildOptions().setLogging("LEGACY");

    return new Build().setSteps(Collections.singletonList(buildStep)).setOptions(buildOptions);
  }
}
