/*
 * Copyright 2017 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.model.DataProvider
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/v1/data")
class DataController {

  List<DataProvider> dataProviders

  @Autowired
  DataController(Optional<List<DataProvider>> dataProviders) {
    if (dataProviders.present) {
      this.dataProviders = dataProviders.get()
    } else {
      this.dataProviders = []
    }
  }

  @RequestMapping(value = "/static/{id}", method = RequestMethod.GET)
  Object getStaticData(@PathVariable("id") String id, @RequestParam Map<String, String> filters) {
    def dataProvider = dataProviders.find { it.supportsIdentifier(DataProvider.IdentifierType.Static, id) }
    if (!dataProvider) {
      throw new NotFoundException("No data available (id: ${id})")
    }

    def account = dataProvider.getAccountForIdentifier(DataProvider.IdentifierType.Static, id)
    verifyAccessToAccount(account)

    return dataProvider.getStaticData(id, filters)
  }

  @RequestMapping(value = "/adhoc/{groupId}/{bucketId}/**")
  StreamingResponseBody getAdhocData(@PathVariable("groupId") String groupId,
                                     @PathVariable("bucketId") String bucketId,
                                     HttpServletRequest httpServletRequest) {
    def dataProvider = dataProviders.find { it.supportsIdentifier(DataProvider.IdentifierType.Adhoc, groupId) }
    if (!dataProvider) {
      throw new NotFoundException("No data available (groupId: ${groupId})")
    }

    def account = dataProvider.getAccountForIdentifier(DataProvider.IdentifierType.Adhoc, bucketId)
    verifyAccessToAccount(account)

    String pattern = (String) httpServletRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String objectId = new AntPathMatcher().extractPathWithinPattern(pattern, httpServletRequest.getServletPath());

    return new StreamingResponseBody() {
      @Override
      void writeTo (OutputStream outputStream) throws IOException {
        dataProvider.getAdhocData(groupId, bucketId, objectId, outputStream)
      }
    };
  }

  private static void verifyAccessToAccount(String account) {
    def allowedAccounts = (AuthenticatedRequest.getSpinnakerAccounts().orElse(null)?.split(",") ?: []) as Set<String>
    if (!allowedAccounts.contains(account)) {
      throw new AccessDeniedException("Access denied (account: ${account})")
    }
  }
}
