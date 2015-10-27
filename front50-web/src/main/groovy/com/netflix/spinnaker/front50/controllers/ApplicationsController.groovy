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


package com.netflix.spinnaker.front50.controllers

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.events.ApplicationEventListener
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.validator.ApplicationValidator
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
import org.springframework.web.bind.annotation.*

import javax.servlet.http.HttpServletResponse

@Slf4j
@RestController
@RequestMapping(["/default/applications", "/global/applications"])
@Api(value = "application", description = "Application API")
public class ApplicationsController {
  @Autowired
  MessageSource messageSource

  @Autowired
  ApplicationDAO applicationDAO

  @Autowired
  List<ApplicationValidator> applicationValidators

  @Autowired(required = false)
  List<ApplicationEventListener> applicationEventListeners = []

  @Autowired
  Registry registry

  @RequestMapping(value = "/search", method = RequestMethod.GET)
  @ApiOperation(value = "", notes = """Search for applications within a specific `account` given one or more attributes.

- /search?email=my@email.com
- /search?email=my@email.com&name=flex
""")
  Set<Application> search(@RequestParam Map<String, String> params) {
    return getApplication().search(params)
  }

  @ApiOperation(value = "", notes = "Fetch all applications within a specific `account`")
  @RequestMapping(method = RequestMethod.GET)
  Set<Application> applications() {
    return getApplication().findAll()
  }

  @ApiOperation(value = "", notes = "Update an existing application within a specific `account`")
  @RequestMapping(method = RequestMethod.PUT)
  Application put(@RequestBody final Application app) {
    def application = getApplication()
    Application existingApplication = application.findByName(app.getName())
    application.initialize(existingApplication).withName(app.getName()).update(app)
    return application
  }

  @ApiOperation(value = "", notes = "Create an application within a specific `account`")
  @RequestMapping(method = RequestMethod.POST, value = "/name/{application:.+}")
  Application post(@RequestBody final Application app) {
    return getApplication().initialize(app).withName(app.getName()).save()
  }

  @ApiOperation(value = "", notes = "Delete an application from a specific `account`")
  @RequestMapping(method = RequestMethod.DELETE, value = "/name/{application:.+}")
  void delete(@PathVariable String application, HttpServletResponse response) {
    getApplication().initialize(new Application().withName(application)).delete()
    response.setStatus(HttpStatus.ACCEPTED.value())
  }

  @ApiOperation(value = "", notes = "Fetch a single application by name within a specific `account`")
  @RequestMapping(method = RequestMethod.GET, value = "/name/{application:.+}")
  Application getByName(@PathVariable final String application) {
    return getApplication().findByName(application)
  }

  @ExceptionHandler(Application.ValidationException)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map handleValidationException(Application.ValidationException ex) {
    def locale = LocaleContextHolder.locale
    def errorStrings = []
    ex.errors.each { Errors errors ->
      errors.allErrors.each { ObjectError objectError ->
        def message = messageSource.getMessage(objectError.code, objectError.arguments, objectError.defaultMessage, locale)
        errorStrings << message
      }
    }
    return [error: "Validation Failed.", errors: errorStrings, status: HttpStatus.BAD_REQUEST]
  }

  private Application getApplication() {
    return new Application(
        dao: applicationDAO,
        validators: applicationValidators,
        applicationEventListeners: applicationEventListeners
    )
  }
}
