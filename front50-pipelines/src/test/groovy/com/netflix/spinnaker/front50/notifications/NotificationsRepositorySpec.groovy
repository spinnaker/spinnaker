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

package com.netflix.spinnaker.front50.notifications

import com.netflix.spinnaker.front50.model.notification.Notification
import com.netflix.spinnaker.front50.utils.CassandraTestHelper
import spock.lang.Shared
import spock.lang.Specification

class NotificationsRepositorySpec extends Specification{

    @Shared
    CassandraTestHelper cassandraTestHelper = new CassandraTestHelper()

    @Shared
    NotificationRepository repo

    void setupSpec() {
        repo = new NotificationRepository()
        repo.keyspace = cassandraTestHelper.keyspace
        repo.init()
    }

    void cleanup() {
        cassandraTestHelper.keyspace.truncateColumnFamily(NotificationRepository.CF_NOTIFICATIONS)
    }

    void setup() {
        cassandraTestHelper.keyspace.truncateColumnFamily(NotificationRepository.CF_NOTIFICATIONS)
    }

    void 'globals can be saved, retrieved, and overwritten'() {
        when:
        repo.saveGlobal(
                [email: [
                        [address: 'tyrionl@netflix.com', when: ['pipeline.failed']]
                ]
                ] as Notification
        )

        and:
        Map global = repo.getGlobal()

        then:
        global.email.size() == 1
        global.email[0].address == 'tyrionl@netflix.com'
        global.email[0].level == 'global'

        when:
        repo.saveGlobal(
                [email: [
                        [address: 'tywinl@netflix.com', when: ['tasks.failed']]
                ]
                ] as Notification
        )

        and:
        global = repo.getGlobal()

        then:
        global.email.size() == 1
        global.email[0].address == 'tywinl@netflix.com'
        global.email[0].level == 'global'

    }
}
