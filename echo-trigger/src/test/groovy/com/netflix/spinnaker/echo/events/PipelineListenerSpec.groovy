/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.events

import com.netflix.spinnaker.echo.model.Event
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Pipeline listener tests
 */
class PipelineListenerSpec extends Specification {

    static final String MASTER = 'master1'
    static final String JOB = 'job1'
    static final Map PIPELINE = ['name': 'pipeline']
    static final Map PIPELINE2 = ['name': 'pipeline2']

    @Subject
    PipelineListener listener = new PipelineListener()
    MayoService mayoService
    OrcaService orcaService
    Event event

    void setup() {
        mayoService = Mock(MayoService)
        orcaService = Mock(OrcaService)
        listener.orca = orcaService
        listener.mayo = mayoService
        listener.jobsList = [master1: ['job1']]
        event = new Event(details: [source: 'igor'], content: [master: MASTER, jobName: JOB])
    }

    @Unroll
    void 'does nothing when #service is not defined'() {
        given:
        listener."$service" = null

        when:
        listener.processEvent(event)

        then:
        0 * _

        where:
        service << ['orca', 'mayo']
    }

    void 'triggers a pipeline build event when there is a job match'() {
        when:
        listener.processEvent(event)

        then:
        1 * mayoService.getPipelines(MASTER, JOB) >> [PIPELINE]
        1 * orcaService.triggerBuild(PIPELINE) >> 'tasks/1'
    }

    void 'triggers a pipeline build event for every pipeline'() {
        when:
        listener.processEvent(event)

        then:
        1 * mayoService.getPipelines(MASTER, JOB) >> [PIPELINE, PIPELINE2]
        1 * orcaService.triggerBuild(PIPELINE) >> 'tasks/1'
        1 * orcaService.triggerBuild(PIPELINE2) >> 'tasks/1'
    }

    void 'does not trigger a pipeline build for no pipelines'() {
        when:
        listener.processEvent(event)

        then:
        1 * mayoService.getPipelines(MASTER, JOB) >> []
        0 * orcaService.triggerBuild(_)
    }

    void 'does not trigger a build when the event is not found'() {
        given:
        listener.jobsList = [master1: []]

        when:
        listener.processEvent(event)

        then:
        0 * _

    }

}
