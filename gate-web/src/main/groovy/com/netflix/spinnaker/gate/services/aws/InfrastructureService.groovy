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
import com.netflix.spinnaker.gate.services.internal.MortService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class InfrastructureService {

  private static final String GROUP = "infrastructure"

  @Autowired
  MortService mortService

  private static <T extends List> HystrixCommand<T> command(String type, Closure<T> work) {
    (HystrixCommand<T>)HystrixFactory.newListCommand(GROUP, type, work)
  }

  List<Map> getInstanceTypes() {
    command("instanceTypes") {
      mortService.instanceTypes
    } execute()
  }

  List<Map> getKeyPairs() {
    command("keyPairs") {
      mortService.keyPairs
    } execute()
  }

  List<Map> getSubnets() {
    command("subnets") {
      mortService.subnets
    } execute()
  }

  @Deprecated
  List<Map> getVpcs() {
    command("vpcs") {
      mortService.getNetworks('aws')
    } execute()
  }
}
