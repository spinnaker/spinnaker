package com.netflix.spinnaker.echo

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * Enables event search
 */
@RestController
class SearchController {

    @Autowired SearchIndex searchIndex

    @RequestMapping(value = '/search', method = RequestMethod.GET)
    List<Map> search() {
    }

    @RequestMapping(value = '/search/list', method = RequestMethod.GET)
    List<Map> list(){
        searchIndex.list()
    }

}
