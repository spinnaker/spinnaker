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
