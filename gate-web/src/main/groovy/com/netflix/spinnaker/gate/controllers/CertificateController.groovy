/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.CertificateService
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/certificates")
class CertificateController {

  @Autowired
  CertificateService certificateService

  @ApiOperation(value = "Retrieve a list of all server certificates")
  @RequestMapping(method = RequestMethod.GET)
  List<Map> all(@RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    certificateService.getCertificates(sourceApp)
  }

  @ApiOperation(value = "Retrieve a list of server certificates for a given cloud provider")
  @RequestMapping(value = "/{cloudProvider}", method = RequestMethod.GET)
  List<Map> allByCloudProvider(@PathVariable String cloudProvider,
                               @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    certificateService.getCertificates(cloudProvider, sourceApp)
  }
}
