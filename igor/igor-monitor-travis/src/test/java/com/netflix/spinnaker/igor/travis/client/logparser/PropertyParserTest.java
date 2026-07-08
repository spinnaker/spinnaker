/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.client.logparser;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PropertyParserTest {
  @Test
  void extractPropertiesFromLog() {
    String buildLog =
        "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/nice/path/some-package_0.0.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n"
            + "[Thread 0] Artifactory response: 201 Created";

    Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog);

    assertEquals(0, properties.size());
  }

  @Test
  void extractPropertiesFromLogWorks() {
    String buildLog = "SPINNAKER_PROPERTY_MY_PROPERTY=MYVALUE\r";

    Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog);

    assertEquals(1, properties.size());
  }

  @Test
  void extractPropertiesFromLogWithJSON() {
    String buildLog = "SPINNAKER_CONFIG_JSON={\"key1\":\"value1\"}\r";

    Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog);

    assertEquals(1, properties.size());
  }

  @Test
  void extractPropertiesFromLogWithJSONAnd1PropertyWorks() {
    String buildLog =
        "SPINNAKER_PROPERTY_MY_PROPERTY=MYVALUE\n"
            + "SPINNAKER_CONFIG_JSON={\"key1\":\"value1\"}\r";

    Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog);

    assertEquals(2, properties.size());
  }

  @Test
  void extractPropertiesFromLogWithMalformedJSONThrowsException() {
    String buildLog = "SPINNAKER_CONFIG_JSON={\"key1\";\"value1\"}\r";

    assertThrows(JsonParseException.class, () -> PropertyParser.extractPropertiesFromLog(buildLog));
  }

  @Test
  void doNotDetectJsonMagicStringIfItIsNotFirstNonWhitespaceSubstringInTheLine() {
    String buildLog = "some log SPINNAKER_CONFIG_JSON={\"key1\":\"value1\"}\r\n";
    buildLog += "[32;1m$ echo \"SPINNAKER_PROPERTY_HELLO_WORLD=ello world\"[0;m\n";

    Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog);

    assertEquals(0, properties.size());
  }

  @Test
  void doNotExtractPropertiesFromLogIfBuildLogsHaveScriptStepsBeforeOutput() {
    String testValue = "hello world";
    String testKey = "HELLO_WORLD";
    String buildLog = "$ echo \"SPINNAKER_PROPERTY_" + testKey + "=i should not appear\"\n";
    buildLog += "SPINNAKER_PROPERTY_" + testKey + "=" + testValue;

    Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog);

    assertEquals(1, properties.size());
    assertEquals(testValue, properties.getOrDefault(testKey.toLowerCase(), "").toString());
  }

  @Test
  void doNotExtractPropertiesFromLogIfKeyIsNotAtStartOfFile() {
    String buildLog = "????SPINNAKER_PROPERTY_HELLO_WORLD=hello world";

    Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog);

    assertEquals(0, properties.size());
  }

  @Test
  void doExtractPropertiesFromLogIfKeyIsNotAtStartOfFileButItIsOnlyWhitespace() {
    String buildLog = "     SPINNAKER_PROPERTY_HELLO_WORLD=hello world";

    Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog);

    assertEquals(1, properties.size());
  }
}
