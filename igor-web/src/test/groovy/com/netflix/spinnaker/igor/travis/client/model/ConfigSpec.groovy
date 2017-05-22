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

package com.netflix.spinnaker.igor.travis.client.model

import com.netflix.spinnaker.igor.build.model.GenericParameterDefinition
import spock.lang.Specification
import spock.lang.Unroll


class ConfigSpec extends Specification {

    def "getGenericParameterDefinitionList extracts travis environment variables"() {
        given:
        Config config = new Config([:])
        config.globalEnv = [ "FOO=bar", "BAR=foo" ]

        when:
        List<GenericParameterDefinition> genericParameterDefinitionList = config.getParameterDefinitionList()

        then:
        genericParameterDefinitionList.size() == 2
        genericParameterDefinitionList.first().defaultValue == "bar"
        genericParameterDefinitionList.get(1).name == "BAR"
    }

    def "getGenericParameterDefinitionList handles null"() {
        given:
        Config config = new Config([:])
        config.globalEnv = null

        when:
        List<GenericParameterDefinition> genericParameterDefinitionList = config.getParameterDefinitionList()

        then:
        genericParameterDefinitionList.size() == 0
    }

    def "getGenericParameterDefinitionList handles = in value"() {
        given:
        Config config = new Config([:])
        config.globalEnv = ['FOO="foo=bar"']

        when:
        List<GenericParameterDefinition> genericParameterDefinitionList = config.getParameterDefinitionList()

        then:
        genericParameterDefinitionList.size() == 1
        genericParameterDefinitionList.first().defaultValue == '"foo=bar"'
    }

    @Unroll
    def "should handle different permutations of queryParameters"() {
        when:
        Config config = new Config(queryParameters)

        then:
        config.env == expectedEnv

        where:
        queryParameters        || expectedEnv
        [FOO:"bar"]            || [matrix: "FOO=bar"]
        [FOO:"bar", BAR:"foo"] || [matrix: "FOO=bar BAR=foo"]
        null                   || null
    }

}
