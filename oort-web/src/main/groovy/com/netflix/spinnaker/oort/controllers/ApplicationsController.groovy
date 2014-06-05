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

import com.netflix.spinnaker.oort.model.Application
import com.netflix.spinnaker.oort.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/applications")
class ApplicationsController {

  @Autowired
  List<ApplicationProvider> applicationProviders

  @Autowired
  MessageSource messageSource

  @RequestMapping(method = RequestMethod.GET)
  List<Application> list() {
    def results = applicationProviders.collectMany {
      it.applications ?: []
    }
    results.removeAll([null])
    results.sort { a, b -> a?.name?.toLowerCase() <=> b?.name?.toLowerCase() }
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.GET)
  Application get(@PathVariable String name) {
    try {
      def apps = applicationProviders.collect {
        it.getApplication(name)
      }
      if (!apps) {
        throw new ApplicationNotFoundException(name: name)
      } else {
        Application result = null
        for (Application app in apps) {
          if (!result) {
            result = new ApplicationViewModel(name: app.name, attributes: app.attributes, clusterNames: app.clusterNames)
          } else {
            def clusterNames = Application.mergeClusters.curry(app, result).call()
            result = new ApplicationViewModel(name: app.name, attributes: app.attributes + result.attributes, clusterNames: clusterNames)
          }
        }
        result
      }
    } catch (IGNORE) {
      throw new ApplicationNotFoundException(name: name)
    }
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map applicationNotFoundExceptionHandler(ApplicationNotFoundException ex) {
    def message = messageSource.getMessage("application.not.found", [ex.name] as String[], "application.not.found", LocaleContextHolder.locale)
    [error: "application.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  static class ApplicationNotFoundException extends RuntimeException {
    String name
  }

  static class ApplicationViewModel implements Application {
    String name
    Map<String, String> attributes
    Map<String, Set<String>> clusterNames
  }
}
