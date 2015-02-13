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

package com.netflix.spinnaker.front50.controllers

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.front50.model.application.GlobalAccountCredentials
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/credentials")
@RestController
@Api(value = "credential", description = "Credential API")
class CredentialsController {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  ExtendedRegistry extendedRegistry

  @ApiOperation(value = "", notes = "Fetch all account details")
  @RequestMapping(method = RequestMethod.GET)
  List<Map> list() {
    return accountCredentialsProvider.all.collect { [name: it.name, global: it instanceof GlobalAccountCredentials] }
  }
}
