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

package com.netflix.spinnaker.echo

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
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

    @RequestMapping(value = '/search/get/{source}/{type}/{id}', method = RequestMethod.GET)
    Map get(
        @PathVariable(value = 'source') String source,
        @PathVariable(value = 'type') String type,
        @PathVariable(value = 'id') String id) {
        searchIndex.get(source, type, id)
    }

    @RequestMapping(value = '/search/events/{start}', method = RequestMethod.GET)
    Map eventsByDate(
        @PathVariable(value = 'start') String start,
        @RequestParam(value = 'end', required = false) String end,
        @RequestParam(value = 'source', required = false) String source,
        @RequestParam(value = 'type', required = false) String type,
        @RequestParam(value = 'organization', required = false) String organization,
        @RequestParam(value = 'project', required = false) String project,
        @RequestParam(value = 'application', required = false) String application,
        @RequestParam(value = 'full', required = false) String full,
        @RequestParam(value = 'from', required = false) String from,
        @RequestParam(value = 'size', required = false) String size
    ) {
        searchIndex.searchEvents(
            start,
            end,
            source,
            type,
            organization,
            project,
            application,
            Boolean.parseBoolean(full),
            Integer.parseInt(from ?: '0'),
            Integer.parseInt(size ?: '10')
        )
    }

    @RequestMapping(value = '/search/direct/{source}/{type}', method = RequestMethod.POST)
    String directSearch(
        @PathVariable(value = 'source') String source,
        @PathVariable(value = 'type') String type,
        @RequestBody String query) {
        searchIndex.directSearch(source, type, query)
    }

    @RequestMapping(value = '/search/direct/metadata', method = RequestMethod.POST)
    String directSearchMetadata(@RequestBody String query) {
        searchIndex.directSearchMetadata(query)
    }

}
