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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchResultSet
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SearchController {

  protected static final Logger log = Logger.getLogger(this)

  @Autowired
  List<SearchProvider> searchProviders

  /**
   * Simple search endpoint that delegates to {@link SearchProvider}s.
   * @param query the phrase to query
   * @param type (optional) a filter, used to only return results of that type. If no value is supplied, all types will be returned
   * @param platform a filter, used to only return results from providers whose platform value matches this
   * @param pageNumber the page number, starting with 1
   * @param pageSize the maximum number of results to return per page
   * @param filters (optional) a map of key-value pairs to further filter the keys
   * @return a list {@link SearchResultSet)s
   */
  @RequestMapping(value = '/search')
  List<SearchResultSet> search(SearchQueryCommand q) {

    log.info("Fetching search results for ${q.q}, platform: ${q.platform}, type: ${q.type}, pageSize: ${q.pageSize}, pageNumber: ${q.page}, filter: ${q.filter}")

    def providers = q.platform ?
      searchProviders.findAll { it.platform == q.platform } :
      searchProviders

    List<SearchResultSet> results = providers.collect {
      if (q.type && !q.type.isEmpty()) {
        it.search(q.q, q.type, q.page, q.pageSize, q.filter)
      } else {
        it.search(q.q, q.page, q.pageSize, q.filter)
      }
    }

    if (results.size() == 1) {
      results
    } else {

      int total = results.inject(0) { acc, item -> acc + item.totalMatches }
      List<Map<String, String>> allResults = results.inject([]) { acc, item -> acc.addAll(item.results); acc }

      //TODO-cfieber: this is a temporary workaround to https://github.com/spinnaker/deck/issues/128
      [new SearchResultSet(
        totalMatches: total,
        pageNumber: q.page,
        pageSize: q.pageSize,
        platform: 'aws', //TODO-cfieber: hardcoding this for now...
        query: q.q,
        results: allResults)]
    }
  }

  static class SearchQueryCommand {
    /**
     * the phrase to query
     */
    String q

    /**
     * (optional) a filter, used to only return results of that type. If no value is supplied, all types will be returned
     */
    List<String> type

    /**
     * a filter, used to only return results from providers whose platform value matches this
     */
    String platform = ''

    /**
     * the page number, starting with 1
     */
    Integer page = 1

    /**
     * the maximum number of results to return per page
     */
    Integer pageSize = 10

    /**
     * (optional) a map of ad-hoc key-value pairs to further filter the keys,
     * based on the map provided by {@link com.netflix.spinnaker.oort.aws.data.Keys#parse(java.lang.String)}
     * potential matches must fully intersect the filter map entries
     */
    Map<String, String> filter
  }

}
