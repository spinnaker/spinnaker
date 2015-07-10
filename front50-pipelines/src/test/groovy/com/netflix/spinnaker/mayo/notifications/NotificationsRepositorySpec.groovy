/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.mayo.notifications

import com.netflix.astyanax.Keyspace
import com.netflix.spinnaker.mayo.Application
import com.netflix.spinnaker.mayo.HierarchicalLevel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import spock.lang.Specification

@WebAppConfiguration
@ContextConfiguration(classes = [Application])
class NotificationsRepositorySpec extends Specification {

    @Autowired
    NotificationRepository repo

    @Autowired
    Keyspace keyspace

    @Autowired
    ApplicationContext context

    void setupSpec() {
        System.setProperty('netflix.environment', 'test')
        System.setProperty('spinnaker.cassandra.cluster', 'workflow')
        System.setProperty('spinnaker.cassandra.keyspace', 'test')
    }

    void cleanup() {
        keyspace.truncateColumnFamily(NotificationRepository.CF_NOTIFICATIONS)
    }

    void setup() {
        keyspace.truncateColumnFamily(NotificationRepository.CF_NOTIFICATIONS)
    }

    void 'globals can be saved, retrieved, and overwritten'() {
        when:
        repo.saveGlobal(
            [ email: [
                [address: 'tyrionl@netflix.com', when: ['pipeline.failed'] ]
              ]
            ]
        )

        and:
        Map global = repo.getGlobal()

        then:
        global.email.size() == 1
        global.email[0].address == 'tyrionl@netflix.com'
        global.email[0].level == 'global'

        when:
        repo.saveGlobal(
            [ email: [
                [address: 'tywinl@netflix.com', when: ['tasks.failed'] ]
              ]
            ]
        )

        and:
        global = repo.getGlobal()

        then:
        global.email.size() == 1
        global.email[0].address == 'tywinl@netflix.com'
        global.email[0].level == 'global'

    }

    void 'globals are folded into application notifications'() {
        when:
        repo.saveGlobal(
            [ email: [
                [address: 'tyrionl@netflix.com', when: ['pipeline.failed'] ]
              ]
            ]
        )
        repo.save(HierarchicalLevel.APPLICATION, 'mayo',
            [ email: [
                [address: 'orca@netflix.com', when: ['tasks.failed'] ]
              ]
            ]
        )

        and:
        Map notifications = repo.get(HierarchicalLevel.APPLICATION, 'mayo')

        then:
        notifications.email.size() == 2

        notifications.email[0].address == 'orca@netflix.com'
        notifications.email[0].when == ['tasks.failed']
        notifications.email[0].level == 'application'

        notifications.email[1].address == 'tyrionl@netflix.com'
        notifications.email[1].when == ['pipeline.failed']
        notifications.email[1].level == 'global'
    }

    void 'globals are folded in when application does not exist'() {
        when:
        repo.saveGlobal(
            [ email: [
                [address: 'tyrionl@netflix.com', when: ['pipeline.failed'] ]
              ]
            ]
        )

        and:
        Map nonexistentApp = repo.get(HierarchicalLevel.APPLICATION, 'does-not-exist')

        then:
        nonexistentApp == repo.getGlobal()

    }

}
