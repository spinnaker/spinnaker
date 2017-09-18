/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.internal.KayentaService
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnExpression('${services.kayenta.enabled:false}')
class V2CanaryController {
  @Autowired
  KayentaService kayentaService

  @ApiOperation(value = "Retrieve a list of configured Kayenta accounts")
  @RequestMapping(value = '/v2/canaries/credentials', method = RequestMethod.GET)
  List listCredentials() {
    kayentaService.getCredentials()
  }

  @ApiOperation(value = "Retrieve a list of all configured canary judges")
  @RequestMapping(value = "/v2/canaries/judges", method = RequestMethod.GET)
  List listJudges() {
    kayentaService.listJudges()
  }
}
