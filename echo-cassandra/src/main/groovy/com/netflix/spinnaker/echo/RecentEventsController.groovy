package com.netflix.spinnaker.echo

import com.netflix.spinnaker.echo.cassandra.TimeSeriesRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * An endpoint to get most recent events of a particular type
 */
@RestController
class RecentEventsController {

    @Autowired
    TimeSeriesRepository timeSeriesRepository

    @RequestMapping(value = '/events/recent/{type}/{from}', method = RequestMethod.GET)
    List<Map> get(
        @PathVariable('type') String type,
        @PathVariable('from') long from) {
        timeSeriesRepository.eventsByType(type, from)
    }

}
