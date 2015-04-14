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


package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.NotificationService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/notifications")
@RestController
class NotificationController {
  @Autowired
  NotificationService notificationService

  @RequestMapping(value = "/{type}/{application:.+}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String type, @PathVariable String application) {
    notificationService.deleteNotificationConfig(type, application)
  }

  @RequestMapping(value = "/{type}/{application:.+}", method = RequestMethod.POST)
  void saveNotificationConfig(@PathVariable String type, @PathVariable String application, @RequestBody Map notificationConfig) {
    notificationService.saveNotificationConfig(type, application, notificationConfig)
  }

  @RequestMapping(value = "/{type}/{application:.+}", method = RequestMethod.GET)
  Map getNotificationConfig(@PathVariable String type, @PathVariable String application) {
    notificationService.getNotificationConfigs(type, application)
  }

}
