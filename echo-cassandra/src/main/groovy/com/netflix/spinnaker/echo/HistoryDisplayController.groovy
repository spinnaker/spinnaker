package com.netflix.spinnaker.echo

import com.netflix.spinnaker.echo.cassandra.HistoryRepository
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
class HistoryDisplayController {

    @Autowired
    HistoryRepository historyRepository

    @RequestMapping(value = '/history/dump', method = RequestMethod.GET)
    List<Map> get() {
        historyRepository.today()
    }

}
