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
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState
import spock.lang.Specification
import spock.lang.Unroll

class TravisResultConverterSpec extends Specification {

    @Unroll
    def "convert travis build states to Result object state='#travisBuildState' should give Result=#expectedResult"() {
        expect:
        TravisResultConverter.getResultFromTravisState(travisBuildState) == expectedResult

        where:
        travisBuildState || expectedResult
        TravisBuildState.started  || Result.BUILDING
        TravisBuildState.passed   || Result.SUCCESS
        TravisBuildState.errored  || Result.FAILURE
    }

    @Unroll
    def "check if build is running state='#travisBuildState' should give running=#expectedResult"() {
        expect:
        TravisResultConverter.running(travisBuildState) == expectedResult

        where:
        travisBuildState || expectedResult
        TravisBuildState.started  || true
        TravisBuildState.created  || true
        TravisBuildState.passed   || false
        TravisBuildState.errored  || false
    }
}
