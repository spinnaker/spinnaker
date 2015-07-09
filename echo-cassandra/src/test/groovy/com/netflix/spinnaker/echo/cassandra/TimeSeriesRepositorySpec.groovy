/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.cassandra

import com.netflix.spinnaker.echo.model.Event
import spock.lang.Shared

/**
 * Test for time series repository
 */
@SuppressWarnings(['DuplicateMapLiteral', 'DuplicateNumberLiteral'])
class TimeSeriesRepositorySpec extends AbstractCassandraBackedSpec {

    @Shared
    TimeSeriesRepository repo

    void setupSpec() {
        repo = new TimeSeriesRepository()
        repo.keyspace = keyspace
        repo.onApplicationEvent(null)
    }

    void setup() {
        repo.runQuery '''TRUNCATE events_time_series;'''
    }

    void 'events with no type are classified as unknown'() {
        given:
        long now = new Date().time

        when:
        repo.add(new Event(details: [:], content: [:]))

        then:
        repo.eventsByType('UNKNOWN', now).size == 1
    }

    void 'should return an empty list if there are no events'() {
        expect:
        repo.eventsByType('NOTHERE', 0) == []
    }

    void 'should filter events by type'() {
        when:
        repo.add(new Event(details: [type: 'UNFILTERED'], content: [:]))
        repo.add(new Event(details: [type: 'FILTERED'], content: [data: '111']))

        then:
        repo.eventsByType('FILTERED', 0).size == 1
        repo.eventsByType('FILTERED', 0).first().content.data == '111'
    }

    void 'should exclude events before requested day'() {
        given:
        10.times {
            repo.add(new Event(details: [type: 'FILTERED'], content: [data: 'first']))
        }
        when:
        long requestTime = new Date().time
        and:
        5.times {
            repo.add(new Event(details: [type: 'FILTERED'], content: [data: 'second']))
        }
        def listCreatedAfterRequestTime = repo.eventsByType('FILTERED', requestTime)
        then:
        repo.eventsByType('FILTERED', 0).size == 15
        listCreatedAfterRequestTime.size == 5
        listCreatedAfterRequestTime.collect { it.content.data }.unique() == ['second']
    }

}
