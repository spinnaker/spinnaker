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

package com.netflix.spinnaker.cats.agent

import spock.lang.Specification

class CompositeExecutionInstrumentationSpec extends Specification {

    def 'calls all instrumentations'() {
        setup:
        ExecutionInstrumentation e1 = Mock(ExecutionInstrumentation)
        ExecutionInstrumentation e2 = Mock(ExecutionInstrumentation)
        CompositeExecutionInstrumentation subj = new CompositeExecutionInstrumentation(Arrays.asList(e1, e2))
        CachingAgent agent = Stub(CachingAgent)
        Throwable cause = new RuntimeException('bewm')

        when:
        subj.executionStarted(agent)

        then:
        1 * e1.executionStarted(agent)
        1 * e2.executionStarted(agent)

        when:
        subj.executionCompleted(agent)

        then:
        1 * e1.executionCompleted(agent)
        1 * e2.executionCompleted(agent)

        when:
        subj.executionFailed(agent, cause)

        then:
        1 * e1.executionFailed(agent, cause)
        1 * e2.executionFailed(agent, cause)
    }
}
