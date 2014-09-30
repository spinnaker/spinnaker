/*
 * Copyright 2014 Netflix, Inc.
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



package com.netflix.spinnaker.mort.web

import com.netflix.spinnaker.mort.search.SearchProvider
import com.netflix.spinnaker.mort.search.SearchResultSet
import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * TODO: Copied from oort; should refactor to common library
 */
@CompileStatic
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
   * @return a list {@link SearchResultSet)s
   */
  @RequestMapping(value = '/search')
  List<SearchResultSet> search(
    @RequestParam(value = 'q') String query,
    @RequestParam(value = 'type', required = false) List<String> types,
    @RequestParam(value = 'platform', defaultValue = '') String platform,
    @RequestParam(value = 'page', defaultValue = '1') Integer pageNumber,
    @RequestParam(value = 'pageSize', defaultValue = '10') Integer pageSize) {

    log.info("Fetching search results for ${query}, platform: ${platform}, type: ${types}, pageSize: ${pageSize}, pageNumber: ${pageNumber}")

    def providers = platform ? searchProviders.findAll {
      it.platform == platform
    } : searchProviders

    providers.collect {
      if (types && !types.isEmpty()) {
        it.search(query, types, pageNumber, pageSize)
      } else {
        it.search(query, pageNumber, pageSize)
      }
    }
  }
}
