/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.kayenta.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.kayenta.atlas.config.KayentaSerializationConfigurationProperties
import com.netflix.kayenta.config.KayentaConfiguration
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

class KayentaSerializationConfigurationPropertiesTest extends Specification {
    private final String testInstantString = "1970-01-01T01:01:01Z"
    private final Instant instant = Instant.parse(testInstantString)
    private final Duration duration = Duration.between(Instant.EPOCH, instant)

    @Unroll
    void "Test Data and Duration serialization - #description"() {
        setup:
        ObjectMapper objectMapper = new ObjectMapper()
        KayentaSerializationConfigurationProperties properties = new KayentaSerializationConfigurationProperties()
        properties.setWriteDatesAsTimestamps(datesAsTimestamps)
        properties.setWriteDurationsAsTimestamps(durationsAsTimestamps)
        KayentaConfiguration.configureObjectMapperFeatures(objectMapper, properties)

        when:
        StringWriter jsonStream = new StringWriter()
        objectMapper.writeValue(jsonStream, instant)
        String instantJson = jsonStream.toString()

        and:
        jsonStream = new StringWriter()
        objectMapper.writeValue(jsonStream, duration)
        String durationJson = jsonStream.toString()

        then:
        instantJson.replace('"', '') == instantString
        durationJson.replace('"', '')  == durationString

        where:
        description                                  | datesAsTimestamps | durationsAsTimestamps || instantString          | durationString
        "Dates as String, Durations as String"       | false             | false                 || "1970-01-01T01:01:01Z" | "PT1H1M1S"
        "Dates as Timestamp, Durations as String"    | true              | false                 || "3661.000000000"       | "PT1H1M1S"
        "Dates as String, Durations as Timestamp"    | false             | true                  || "1970-01-01T01:01:01Z" | "3661.000000000"
        "Dates as Timestamp, Durations as Timestamp" | true              | true                  || "3661.000000000"       | "3661.000000000"
    }
}
