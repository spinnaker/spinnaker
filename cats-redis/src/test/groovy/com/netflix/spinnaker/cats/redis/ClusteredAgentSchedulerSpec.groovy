/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.cats.redis

import spock.lang.Specification

class ClusteredAgentSchedulerSpec extends Specification {
    def 'cache run aborted if agent doesnt acquire execution token'() {
        expect: 'this should get impld'
        false
    }

    def 'cache run proceeds if agent aquires execution token'() {
        expect: 'this should get impld'
        false
    }

    def 'execution token is TTLd when agent execution completes successfully'() {
        expect: 'this should get impld'
        false
    }

    def 'execution token is TTLd when agent execution fails'() {
        expect: 'this should get impld'
        false
    }
}
