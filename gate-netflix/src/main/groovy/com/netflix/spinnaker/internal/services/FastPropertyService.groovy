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
    def command =  HystrixFactory.newMapCommand(GROUP, "getByAppName", true) {
      maheService.getFastPropertiesByApplication(appName)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encoutered for FastPropertyService.getFastPropertiesByApplication(${appName})")
        throw new ThrottledRequestException("Unable to retrieve fast properies by app namefor '${appName}'")
      }
    }
  }

  Map getAll() {
    def command = HystrixFactory.newMapCommand(GROUP, "getAll", true) {
      maheService.getAll()
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.getAll()")
        throw new ThrottledRequestException("Unable to retrieve all fast properties")
      }
    }
  }

  Map create(Map fastProperty) {
    def command = HystrixFactory.newMapCommand(GROUP, "create", true) {
      maheService.create(fastProperty)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.create(${fastProperty})")
        throw new ThrottledRequestException("Unable to create fast property")
      }
    }
  }

  String promote(Map fastProperty) {
    def command = HystrixFactory.newStringCommand(GROUP, "promote", true) {
      maheService.promote(fastProperty)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.promote(${fastProperty})")
        throw new ThrottledRequestException("Unable to promote fast property")
      }
    }
  }

  Map promotionStatus(String promotionId) {
    def command = HystrixFactory.newMapCommand(GROUP, "promotionStatus", true) {
      maheService.promotionStatus(promotionId)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.promotionStatus(${promotionId})")
        throw new ThrottledRequestException("Unable to retrieve fast property promotion status")
      }
    }
  }

  Map passPromotion(String promotionId, Boolean pass) {
    def command = HystrixFactory.newMapCommand(GROUP, "passPromotion", true) {
      maheService.passPromotion(promotionId, pass)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.passPromotion(${promotionId}, ${pass})")
        throw new ThrottledRequestException("Unable to pass fast property pomotion")
      }
    }
  }

  List promotions() {
    def command = HystrixFactory.newListCommand(GROUP, "promotions", true){
      maheService.promotions()
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.promotions()")
        throw new ThrottledRequestException("Unable to retrieve all fast properties promotions")
      }
    }
  }

  List promotionsByApp(String appId) {
    def command = HystrixFactory.newListCommand(GROUP, "promotionsByApp", true) {
      maheService.promotionsByApp(appId)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.promotionsByApp(${appId})")
        throw new ThrottledRequestException("Unable to retrieve promotions by app")
      }
    }
  }

  Map delete(String propId, String cmcTicket, String env) {
    def command = HystrixFactory.newMapCommand(GROUP, "delete", true)  {
      maheService.delete(propId, cmcTicket, env)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.delete(${propId}, ${cmcTicket}, ${env})")
        throw new ThrottledRequestException("Unable to delete promotions by propId: ${propId} in env: ${env}")
      }
    }
  }


  Map queryByScope(Map scope) {
    def command = HystrixFactory.newMapCommand(GROUP, "queryByScope", true) {
      maheService.queryScope(scope)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.queryByScope(${scope})")
        throw new ThrottledRequestException("Unable to query by scope for scope ${scope}")
      }
    }
  }

  Map getImpact(Map scope) {
    def command = HystrixFactory.newMapCommand(GROUP, "getImpact", true) {
      maheService.getImpact(scope)
    }

    try {
      return command.execute()
    } finally {
      if(command.isResponseFromFallback()) {
        log.warn("Fallback encountered for FastPropertyService.getImpact(${scope})")
        throw new ThrottledRequestException("Unable to getImpact for scope: ${scope}")
      }
    }
  }
}
