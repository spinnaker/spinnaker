package com.netflix.spinnaker.echo

import com.netflix.spinnaker.echo.cassandra.HistoryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * An endpoint to get most recent events
 */
@RestController
class HistoryDisplayController {

    @Autowired
    HistoryRepository historyRepository

    @RequestMapping(value = '/history/dump/{date}', method = RequestMethod.GET)
    List<Map> get(
        @PathVariable('date') String date
    ) {
        historyRepository.get(date)
    }

}
