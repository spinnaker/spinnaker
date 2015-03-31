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

package com.netflix.spinnaker.oort.controllers

import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.InstanceProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/instances")
class InstanceController {

  @Autowired
  List<InstanceProvider> instanceProviders

  @Autowired
  MessageSource messageSource

  @RequestMapping(value = "/{account}/{region}/{id:.+}", method = RequestMethod.GET)
  Instance getInstance(@PathVariable String account, @PathVariable String region, @PathVariable String id) {
    Collection<Instance> instanceMatches = instanceProviders.findResults {
      it.getInstance(account, region, id)
    }
    if (!instanceMatches) {
      throw new InstanceNotFoundException(name: id)
    }
    instanceMatches ? instanceMatches.first() : null
  }

  static class InstanceNotFoundException extends RuntimeException {
    String name
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map applicationNotFoundExceptionHandler(InstanceNotFoundException ex) {
    def message = messageSource.getMessage("instance.not.found", [ex.name] as String[], "instance.not.found", LocaleContextHolder.locale)
    [error: "instance.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }
}
