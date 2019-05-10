/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.provider.view

import com.fasterxml.jackson.core.JsonParseException
import spock.lang.Specification

class PropertyParserSpec extends Specification {
    def "ExtractPropertiesFromLog"() {
        String buildLog = "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/nice/path/some-package_0.0.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n" +
            "[Thread 0] Artifactory response: 201 Created"
        when:
        Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog)

        then:
        properties.size() == 0
    }

    def "ExtractPropertiesFromLog works"() {
        String buildLog = "SPINNAKER_PROPERTY_MY_PROPERTY=MYVALUE\r"

        when:
        Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog)

        then:
        properties.size() == 1
    }

    def "ExtractPropertiesFromLog with JSON"() {
        String buildLog = "SPINNAKER_CONFIG_JSON={\"key1\":\"value1\"}\r"

        when:
        Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog)

        then:
        properties.size() == 1
    }

    def "ExtractPropertiesFromLog with JSON and 1 property works"() {
        String buildLog = "SPINNAKER_PROPERTY_MY_PROPERTY=MYVALUE\n" +
            "SPINNAKER_CONFIG_JSON={\"key1\":\"value1\"}\r"

        when:
        Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog)

        then:
        properties.size() == 2
    }

    def "ExtractPropertiesFromLog with malformed JSON throws exception"() {
        String buildLog = "SPINNAKER_CONFIG_JSON={\"key1\";\"value1\"}\r"

        when:
        PropertyParser.extractPropertiesFromLog(buildLog)

        then:
        thrown(JsonParseException)
    }

    def "Do not detect json magic string if it is not first non-whitespace substring in the line"() {
        String buildLog = "some log SPINNAKER_CONFIG_JSON={\"key1\":\"value1\"}\r"

        when:
        Map<String, Object> properties = PropertyParser.extractPropertiesFromLog(buildLog)

        then:
        properties.size() == 0
    }
}
