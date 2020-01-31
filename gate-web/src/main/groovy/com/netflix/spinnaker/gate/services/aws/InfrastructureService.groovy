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

import com.netflix.hystrix.HystrixCommand
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.Callable

@CompileStatic
@Component
class InfrastructureService {

  private static final String GROUP = "infrastructure"

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  private static HystrixCommand<List> command(String type, Callable<List> work) {
    (HystrixCommand<List>)HystrixFactory.newListCommand(GROUP, type, work)
  }

  List<Map> getInstanceTypes(String selectorKey = null) {
    command("instanceTypes") {
      clouddriverServiceSelector.select().instanceTypes
    } execute()
  }

  List<Map> getKeyPairs(String selectorKey = null) {
    command("keyPairs") {
      clouddriverServiceSelector.select().keyPairs
    } execute()
  }

  @Deprecated
  List<Map> getSubnets(String selectorKey = null) {
    command("subnets") {
      clouddriverServiceSelector.select().getSubnets('aws')
    } execute()
  }

  @Deprecated
  List<Map> getVpcs(String selectorKey = null) {
    command("vpcs") {
      clouddriverServiceSelector.select().getNetworks('aws')
    } execute()
  }

  List<Map> getFunctions(String selectorKey = null, String functionName, String region, String account) {
    command("functions") {
      clouddriverServiceSelector.select().getFunctions(functionName,region, account)
    } execute()
  }

  List<Map> getApplicationFunctions(String selectorKey = null, String application) {
    command("functions") {
      clouddriverServiceSelector.select().getApplicationFunctions(application)
    } execute()
  }
}
