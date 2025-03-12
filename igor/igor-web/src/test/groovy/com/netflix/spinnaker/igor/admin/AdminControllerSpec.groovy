/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.igor.admin

import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import spock.lang.Specification

class AdminControllerSpec extends Specification {

    def "should fully reindex a poller"() {
        given:
        def monitor = Mock(CommonPollingMonitor)
        def subject = new AdminController(Optional.of([monitor]))

        when:
        subject.fastForward("foo", null)

        then:
        1 * monitor.getName() >> "foo"
        1 * monitor.poll(false)
        0 * _
    }

    def "should reindex a partition in a poller"() {
        given:
        def monitor = Mock(CommonPollingMonitor)
        def subject = new AdminController(Optional.of([monitor]))

        and:
        def silencedContext = new PollContext("covfefe").fastForward()
        def context = Mock(PollContext) {
            getPartitionName() >> "covfefe"
        }

        when:
        subject.fastForward("bar", "covfefe")

        then:
        1 * monitor.getName() >> "bar"
        1 * monitor.getPollContext("covfefe") >> context
        1 * context.fastForward() >> silencedContext
        1 * monitor.pollSingle(silencedContext)
        0 * _
    }

    def "should throw not found if poller isn't found"() {
        given:
        def monitor = Mock(CommonPollingMonitor)
        def subject = new AdminController(Optional.of([monitor]))

        when:
        subject.fastForward("baz", null)

        then:
        thrown(NotFoundException)
        2 * monitor.getName() >> "foo"
        0 * _
    }

    def "should handle no active pollers"() {
        given:
        def subject = new AdminController(Optional.empty())

        when:
        subject.fastForward("baz", null)

        then:
        thrown(NotFoundException)
        0 * _
    }
}
