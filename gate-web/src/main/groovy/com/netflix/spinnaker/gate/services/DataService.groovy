/*
 * Copyright 2017 Netflix, Inc.
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

import java.util.concurrent.Callable;

@CompileStatic
@Component
class DataService {
  private static final String GROUP = "data"

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  private static HystrixCommand<Void> voidCommand(String type, Callable<Void> work) {
    HystrixFactory.newVoidCommand(GROUP, type, work)
  }

  void getStaticData(String id, Map<String, String> filters, OutputStream outputStream) {
    voidCommand("static", {
      def response = clouddriverServiceSelector.select().getStaticData(id, filters)
      outputStream << response.getBody().in()
    }).execute()
  }

  void getAdhocData(String groupId, String bucketId, String objectId, OutputStream outputStream) {
    voidCommand("adhoc", {
      def response = clouddriverServiceSelector.select().getAdhocData(groupId, bucketId, objectId)
      outputStream << response.getBody().in()
    }).execute()
  }
}
