/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class JenkinsTriggerTest {
  private String trigger = "{" +
    "\"type\": \"jenkins\"," +
    "\"master\": \"my-jenkins-master\"," +
    "\"job\": \"my-job\"," +
    "\"buildNumber\": 50," +
    "\"buildInfo\": {" +
    "  \"artifacts\": [" +
    "    {" +
    "      \"displayPath\": \"props\"," +
    "      \"fileName\": \"props\"," +
    "      \"relativePath\": \"properties/props\"" +
    "    }" +
    "  ]," +
    "  \"building\": false," +
    "  \"duration\": 246," +
    "  \"fullDisplayName\": \"PropertiesTest #106\"," +
    "  \"name\": \"PropertiesTest\"," +
    "  \"number\": 106," +
    "  \"result\": \"SUCCESS\"," +
    "  \"scm\": [" +
    "    {" +
    "      \"branch\": \"master\"," +
    "      \"name\": \"refs/remotes/origin/master\"," +
    "      \"remoteUrl\": \"https://github.com/ezimanyi/docs-site-manifest\"," +
    "      \"sha1\": \"8d0e9525df913c3e42a070f515155e6de4d03f86\"" +
    "    }" +
    "  ]," +
    "  \"timestamp\": \"1552776586747\"," +
    "  \"url\": \"http://localhost:5656/job/PropertiesTest/106/\"" +
    "}" +
    "}";

  /**
   * Trigger serialization preserves generic type of {@link BuildInfo}
   * through deserialization/serialization
   */
  @Test
  void jenkinsTriggerSerialization() throws IOException {
    ObjectMapper mapper = new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(SerializationFeature.INDENT_OUTPUT);
    JenkinsTrigger jenkinsTrigger = mapper.readValue(trigger, JenkinsTrigger.class);
    String triggerSerialized = mapper.writeValueAsString(jenkinsTrigger);
    assertThat(triggerSerialized)
      .contains("\"fileName\" : \"props\"")
      .contains("\"relativePath\" : \"properties/props\"");
  }
}