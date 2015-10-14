package com.netflix.spinnaker.internal.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.internal.services.internal.MaheService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@CompileStatic
@Component
class FastPropertyService {
  private static final String GROUP = "fastProperty"

  @Autowired
  MaheService maheService

  Map getByAppName(String appName) {
    HystrixFactory.newMapCommand(GROUP, "getByAppName") {
      maheService.getFastPropertiesByApplication(appName)
    } execute()
  }

  Map getAll() {
    HystrixFactory.newMapCommand(GROUP, "getAll") {
      maheService.getAll()
    } execute()
  }

  Map create(Map fastProperty) {
    HystrixFactory.newMapCommand(GROUP, "create") {
      maheService.create(fastProperty)
    } execute()
  }

  String promote(Map fastProperty) {
    maheService.promote(fastProperty)
  }

  Map promotionStatus(String promotionId) {
    maheService.promotionStatus(promotionId)
  }

  Map passPromotion(String promotionId, Boolean pass) {
    maheService.passPromotion(promotionId, pass)
  }

  List promotions() {
    maheService.promotions()
  }

  List promotionsByApp(String appId) {
    maheService.promotionsByApp(appId)
  }


  Map delete(String propId, String cmcTicket, String env) {
    HystrixFactory.newMapCommand(GROUP, "delete")  {
      maheService.delete(propId, cmcTicket, env)
    } execute()
  }


  Map queryByScope(Map scope) {
    HystrixFactory.newMapCommand(GROUP, "queryByScope") {
      maheService.queryScope(scope)
    } execute()
  }

  Map getImpact(Map scope) {
    HystrixFactory.newMapCommand(GROUP, "getImpact") {
      maheService.getImpact(scope)
    } execute()
  }
}
