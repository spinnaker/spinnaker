package com.netflix.spinnaker.internal.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.internal.services.internal.MaheService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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
@Slf4j
class FastPropertyService {
  private static final String GROUP = "fastProperty"

  @Autowired
  MaheService maheService

  Map getByAppName(String appName) {
    def command =  HystrixFactory.newMapCommand(GROUP, "getByAppName") {
      maheService.getFastPropertiesByApplication(appName)
    }

    return command.execute()
  }

  Map getAll() {
    def command = HystrixFactory.newMapCommand(GROUP, "getAll") {
      maheService.getAll()
    }

    return command.execute()
  }

  Map create(Map fastProperty) {
    def command = HystrixFactory.newMapCommand(GROUP, "create") {
      maheService.create(fastProperty)
    }

    return command.execute()
  }

  String promote(Map fastProperty) {
    def command = HystrixFactory.newStringCommand(GROUP, "promote") {
      maheService.promote(fastProperty)
    }

    return command.execute()
  }

  Map promotionStatus(String promotionId) {
    def command = HystrixFactory.newMapCommand(GROUP, "promotionStatus") {
      maheService.promotionStatus(promotionId)
    }

    return command.execute()
  }

  Map passPromotion(String promotionId, Boolean pass) {
    def command = HystrixFactory.newMapCommand(GROUP, "passPromotion") {
      maheService.passPromotion(promotionId, pass)
    }

    return command.execute()
  }

  List promotions() {
    def command = HystrixFactory.newListCommand(GROUP, "promotions"){
      maheService.promotions()
    }

    return command.execute()
  }

  List promotionsByApp(String appId) {
    def command = HystrixFactory.newListCommand(GROUP, "promotionsByApp") {
      maheService.promotionsByApp(appId)
    }
    return command.execute()
  }

  Map delete(String propId, String cmcTicket, String env) {
    def command = HystrixFactory.newMapCommand(GROUP, "delete")  {
      maheService.delete(propId, cmcTicket, env)
    }
    return command.execute()
  }


  Map queryByScope(Map scope) {
    def command = HystrixFactory.newMapCommand(GROUP, "queryByScope") {
      maheService.queryScope(scope)
    }
    return command.execute()
  }

  Map getImpact(Map scope) {
    def command = HystrixFactory.newMapCommand(GROUP, "getImpact") {
      maheService.getImpact(scope)
    }
    return command.execute()
  }
}
