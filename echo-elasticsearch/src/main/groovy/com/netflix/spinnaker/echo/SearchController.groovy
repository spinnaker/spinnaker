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

package com.netflix.spinnaker.echo

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Enables event search
 */
@RestController
class SearchController {

    @Autowired
    SearchIndex searchIndex

    @RequestMapping(value = '/search/event/{source}/{type}/', method = RequestMethod.GET)
    String search(@PathVariable(value = 'source') String source, @PathVariable(value = 'type') String type) {
        searchIndex.searchEvent(source, type)
    }

    @RequestMapping(value = '/search/eventsByDate/{since}', method = RequestMethod.GET)
    String searchByDate(
        @PathVariable(value = 'since') since,
        @RequestParam(value = 'source') String source,
        @RequestParam(value = 'type') String type,
        @RequestParam(value = 'full') boolean full
    ) {
        searchIndex.searchByDate(since, source, type, full)
    }

}
