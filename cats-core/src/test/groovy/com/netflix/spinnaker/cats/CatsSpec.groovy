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

package com.netflix.spinnaker.cats

import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.test.TestAgent
import com.netflix.spinnaker.cats.test.TestProvider
import com.netflix.spinnaker.cats.test.TestScheduler
import spock.lang.Specification
import spock.lang.Subject


class CatsSpec extends Specification {

    private static final String SG = 'serverGroup'
    private static final String APP = 'application'

    @Subject CatsModule module
    TestScheduler scheduler
    TestAgent sgAgent
    TestAgent appAgent

    def setup() {
        sgAgent = new TestAgent(authoritative: [SG], types: [APP])
        appAgent = new TestAgent(authoritative: [APP])

        Provider provider = new TestProvider(sgAgent, appAgent)
        scheduler = new TestScheduler()
        module = new CatsModule.Builder().scheduler(scheduler).build(provider)
    }

    def 'it should initially have no results'() {

        when:
        scheduler.runAll()

        then:
        module.view.getAll(SG).isEmpty()
        module.view.getAll(APP).isEmpty()
    }

    def 'app created by sg agent'() {
        setup:
        sgAgent.results[SG] << new DefaultCacheData('sg1', [name: 'sg1'], [(APP): ['app1']])
        sgAgent.results[APP] << new DefaultCacheData('app1', [:], [:])

        when:
        scheduler.runAll()

        then:
        def sg1 = module.view.get(SG, 'sg1')
        sg1.id == 'sg1'
        sg1.attributes.name == 'sg1'
        sg1.relationships[APP].size() == 1
        sg1.relationships[APP].first() == 'app1'

        def app1 = module.view.get(APP, 'app1')
        app1.id == 'app1'
        app1.attributes.isEmpty()
        app1.relationships.isEmpty()
    }

    def 'app referenced by sg agent removed by app agent'() {
        setup:
        sgAgent.results[SG] << new DefaultCacheData('sg1', [name: 'sg1'], [(APP): ['app1', 'app2']])
        sgAgent.results[APP] << new DefaultCacheData('app1', [:], [:])
        sgAgent.results[APP] << new DefaultCacheData('app2', [:], [:])

        appAgent.results[APP] << new DefaultCacheData('app1', [name: 'app1', pdKey: 'pageme'], [:])

        when:
        scheduler.runAll()

        then: 'initial state of both agents reflected on first scheduler run'
        def sg1 = module.view.get(SG, 'sg1')
        sg1.id == 'sg1'
        sg1.attributes.name == 'sg1'
        sg1.relationships[APP].size() == 2
        sg1.relationships[APP].containsAll('app1', 'app2')

        def app1 = module.view.get(APP, 'app1')
        app1.id == 'app1'
        app1.attributes.name == 'app1'
        app1.attributes.pdKey == 'pageme'
        app1.relationships.isEmpty()

        def app2 = module.view.get(APP, 'app2')
        app2.id == 'app2'
        app2.attributes.isEmpty()
        app2.relationships.isEmpty()

        when: 'second agent run, authoritative app source will not remove app2 since it didnt create it'
        scheduler.runAll()
        app2 = module.view.get(APP, 'app2')

        then:
        app2 != null
        app2.id == 'app2'

        when: 'third agent run, authoritative app source will remove app1 and enhance app2'
        sgAgent.results[APP].clear()
        appAgent.results[APP].clear()
        appAgent.results[APP] << new DefaultCacheData('app2', [name: 'app2', pdKey: 'pageme2'], [:])
        scheduler.runAll()
        app1 = module.view.get(APP, 'app1')
        app2 = module.view.get(APP, 'app2')

        then:
        app1 == null
        app2.attributes.name == 'app2'
        app2.attributes.pdKey == 'pageme2'


    }
}