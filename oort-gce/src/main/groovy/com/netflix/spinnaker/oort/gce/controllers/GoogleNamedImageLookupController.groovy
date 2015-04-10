/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.controllers

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.oort.gce.model.GoogleResourceRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/gce/images")
class GoogleNamedImageLookupController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleResourceRetriever googleResourceRetriever

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<Map> find(@RequestParam(value = "account", required = false) String account) {
    def imageMap = googleResourceRetriever.imageMap

    if (account) {
      def imageList = imageMap?.get(account) ?: []

      return imageList.collect {
        [ imageName: it.name ]
      }
    } else {
      def results = []

      imageMap?.entrySet().each { Map.Entry<String, List<String>> accountNameToImageNamesEntry ->
        accountNameToImageNamesEntry.value.each {
          results << [ account: accountNameToImageNamesEntry.key, imageName: it.name ]
        }
      }

      return results
    }
  }
}
