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


package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.MortService
import com.netflix.spinnaker.gate.services.internal.OortService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class SearchService {
  private static final String GROUP = "searches"

  @Autowired
  OortService oortService

  @Autowired
  MortService mortService

  List<Map> search(String query, String type, String platform, int pageSize = 10000, int page = 1) {
    HystrixFactory.newListCommand(GROUP, "search-${type}") {
      def oortResults = oortService.search(query, type, platform, pageSize, page)
      def hasOortMatches = oortResults?.get(0)?.totalMatches ? true : false
      return hasOortMatches ? oortResults : mortService.search(query, type, platform, pageSize, page)
    } execute()
  }
}
