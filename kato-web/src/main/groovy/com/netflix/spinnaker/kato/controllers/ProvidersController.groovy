/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.kato.controllers

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.kato.gce.security.GoogleNamedAccountCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/providers")
class ProvidersController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @RequestMapping(method = RequestMethod.GET)
  List<String> list() {
    accountCredentialsProvider.all.collect { accountCredentials ->
      // TODO(duftler): Switch to collecting amos/AccountCredentials.provider when it's available in kato.
      if (accountCredentials instanceof AmazonCredentials) {
        "aws"
      } else if (accountCredentials instanceof GoogleNamedAccountCredentials) {
        "gce"
      } else {
        "unknown"
      }
    }.unique()
  }

}
