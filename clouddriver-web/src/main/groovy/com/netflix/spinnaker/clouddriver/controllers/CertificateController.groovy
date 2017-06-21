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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Network
import com.netflix.spinnaker.clouddriver.model.Certificate
import com.netflix.spinnaker.clouddriver.model.CertificateProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/certificates")
class CertificateController {

  @Autowired
  List<CertificateProvider> certificateProviders

  @RequestMapping(method = RequestMethod.GET)
  List<Certificate> list() {
    certificateProviders.findResults { it.getAll() }
      .flatten()
      .sort { a, b -> a.serverCertificateName.toLowerCase() <=> b.serverCertificateName.toLowerCase() } as List<Certificate>
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}")
  Set<Network> listByCloudProvider(@PathVariable String cloudProvider) {
    certificateProviders.findAll { certificateProvider ->
      certificateProvider.cloudProvider == cloudProvider
    } collectMany {
      it.all
    }
  }
}
