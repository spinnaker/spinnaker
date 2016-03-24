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

package com.netflix.spinnaker.igor.travis.service

import com.netflix.spinnaker.igor.build.model.Result
import spock.lang.Specification

class TravisResultConverterTest extends Specification {
    def "convert travis build states to Result object"() {
        setup:
        def state = travisBuildState

        when:
        Result result = TravisResultConverter.getResultFromTravisState(state)

        then:
        result == expectedResult

        where:
        travisBuildState | expectedResult
        "started"    | Result.BUILDING
        "passed"     | Result.SUCCESS
        "errored"    | Result.FAILURE
    }
}
