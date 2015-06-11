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


package com.netflix.spinnaker.kato.controllers
import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/credentials")
class CredentialsController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @RequestMapping(method = RequestMethod.GET)
  List<Map> list() {
    accountCredentialsProvider.all.collect {[
      name: it.name,
      // TODO(duftler): Switch to using amos/AccountCredentials.provider when it's available in kato.
      type: getType(it)
    ]}
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
  AccountCredentials getAccount(@PathVariable("name") String name) {
    accountCredentialsProvider.getCredentials name
  }

  private static String getType(AccountCredentials accountCredentials) {
    if (accountCredentials instanceof AmazonCredentials) {
      "aws"
    } else if (accountCredentials instanceof GoogleNamedAccountCredentials) {
      "gce"
    } else if (accountCredentials instanceof CloudFoundryAccountCredentials) {
      "cf"
    } else {
      "unknown"
    }
  }

}
