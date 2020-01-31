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

package com.netflix.spinnaker.gate.services

import com.netflix.hystrix.HystrixCommand
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.Callable

@CompileStatic
@Component
class NetworkService {

  private static final String GROUP = "networks"

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  private static HystrixCommand<List> listCommand(String type, Callable<List> work) {
    HystrixFactory.newListCommand(GROUP, type, work)
  }

  private static HystrixCommand<Map> mapCommand(String type, Callable<Map> work) {
    HystrixFactory.newMapCommand(GROUP, type, work)
  }

  Map getNetworks(String selectorKey) {
    mapCommand("networks") {
      clouddriverServiceSelector.select().getNetworks()
    } execute()
  }

  List<Map> getNetworks(String cloudProvider, String selectorKey) {
    listCommand("networks-$cloudProvider") {
      clouddriverServiceSelector.select().getNetworks(cloudProvider)
    } execute()
  }
}
