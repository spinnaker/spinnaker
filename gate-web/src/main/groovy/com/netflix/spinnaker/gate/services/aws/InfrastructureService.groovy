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

package com.netflix.spinnaker.gate.services.aws

import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class InfrastructureService {

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  List<Map> getInstanceTypes(String selectorKey = null) {
    clouddriverServiceSelector.select().instanceTypes
  }

  List<Map> getKeyPairs(String selectorKey = null) {
    clouddriverServiceSelector.select().keyPairs
  }

  @Deprecated
  List<Map> getSubnets(String selectorKey = null) {
    clouddriverServiceSelector.select().getSubnets('aws')
  }

  @Deprecated
  List<Map> getVpcs(String selectorKey = null) {
    clouddriverServiceSelector.select().getNetworks('aws')
  }

  List<Map> getFunctions(String selectorKey = null, String functionName, String region, String account) {
    clouddriverServiceSelector.select().getFunctions(functionName,region, account)
  }

  List<Map> getApplicationFunctions(String selectorKey = null, String application) {
    clouddriverServiceSelector.select().getApplicationFunctions(application)
  }
}
