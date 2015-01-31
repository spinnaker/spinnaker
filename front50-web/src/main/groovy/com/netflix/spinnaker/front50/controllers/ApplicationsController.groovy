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

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAOProvider
import com.netflix.spinnaker.front50.validator.ApplicationValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
import org.springframework.web.bind.annotation.*

import javax.servlet.http.HttpServletResponse

@Slf4j
@RestController
@RequestMapping("/{account}/applications")
public class ApplicationsController {
  @Autowired
  MessageSource messageSource

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  List<ApplicationDAOProvider> applicationDAOProviders

  @Autowired
  List<ApplicationValidator> applicationValidators

  @RequestMapping(value = "/search", method = RequestMethod.GET)
  Set<Application> search(@PathVariable String account, @RequestParam Map<String, String> params) {
    return getApplication(account).search(params)
  }

  @RequestMapping(method = RequestMethod.GET)
  Collection<Application> applications(@PathVariable String account) {
    return getApplication(account).findAll()
  }

  @RequestMapping(method = RequestMethod.PUT)
  Application put(@PathVariable String account, @RequestBody final Application app) {
    def application = getApplication(account)
    Application existingApplication = application.findByName(app.getName())
    application.initialize(existingApplication).withName(app.getName()).update(app.allSetColumnProperties())
    return application
  }

  @RequestMapping(method = RequestMethod.POST, value = "/name/{name:.+}")
  Application post(@PathVariable String account, @RequestBody final Application app) {
    return getApplication(account).initialize(app).withName(app.getName()).save()
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/name/{name:.+}")
  void delete(@PathVariable String account, @PathVariable String name, HttpServletResponse response) {
    getApplication(account).initialize(new Application().withName(name)).delete()
    response.setStatus(HttpStatus.ACCEPTED.value())
  }

  @RequestMapping(method = RequestMethod.GET, value = "/name/{name:.+}")
  Application getByName(@PathVariable String account, @PathVariable final String name) {
    return getApplication(account).findByName(name)
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

  private Application getApplication(String account) {
    return getApplication(accountCredentialsProvider.getCredentials(account))
  }

  private Application getApplication(AccountCredentials accountCredentials) {
    def dao = null
    for (daoProvider in applicationDAOProviders) {
      if (daoProvider.supports(accountCredentials.getClass())) {
        dao = daoProvider.getForAccount(accountCredentials)
        break
      }
    }

    if (!dao) {
      throw new NotFoundException("No account provider found")
    }

    return new Application(dao: dao, validators: applicationValidators)
  }
}
