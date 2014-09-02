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
import com.netflix.spinnaker.front50.exception.NoPrimaryKeyException
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAOProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

import javax.servlet.http.HttpServletResponse

@Slf4j
@RestController
@RequestMapping("/{account}/applications")
public class ApplicationsController extends SpringBootServletInitializer {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  List<ApplicationDAOProvider> applicationDAOProviders

  @RequestMapping(value = "/search", method = RequestMethod.GET)
  Set<Application> search(@PathVariable String account, @RequestParam Map<String, String> params) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    def application = getApplication(credentials)
    try {
      return application.search(params)
    } catch (NotFoundException e) {
      log.info("GET(/applications) -> NotFoundException occurred: ${e.message}")
      throw new NoApplicationsFoundException(e)
    } catch (Throwable thr) {
      log.error("GET(/applications) -> Throwable occurred: ", thr)
      throw new ApplicationException(thr)
    }
  }

  @RequestMapping(method = RequestMethod.GET)
  Collection<Application> applications(@PathVariable String account) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    def application  = getApplication(credentials)
    try {
      return application.findAll()
    } catch (NotFoundException e) {
      log.info("GET(/applications) -> NotFoundException occurred: ${e.message}")
      throw new NoApplicationsFoundException(e)
    } catch (Throwable thr) {
      log.error("GET(/applications) -> Throwable occurred: ", thr)
      throw new ApplicationException(thr)
    }
  }

  @RequestMapping(method = RequestMethod.PUT)
  Application put(@PathVariable String account, @RequestBody final Application app) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    def application = getApplication(credentials)
    try {
      if (app.getName() == null || app.getName().equals("")) {
        throw new ApplicationWithoutNameException("Application must have a name")
      }
      final Application foundApp = application.findByName(app.getName())
      application.initialize(foundApp).withName(app.getName()).update(app.allSetColumnProperties())
      return application
    } catch (NotFoundException e) {
      log.info("PUT::App not found: ${app.name}: ${e.message}")
      throw new ApplicationNotFoundException(e)
    }
  }

  @RequestMapping(method = RequestMethod.POST, value = "/name/{name}")
  Application post(@PathVariable String account, @RequestBody final Application app) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    def application  = getApplication(credentials)
    try {
      return application.initialize(app).withName(app.getName()).save()
    } catch (NoPrimaryKeyException e) {
      log.info("POST:: cannot create app as name and/or email is missing: ${app}: ${e.message}")
      throw new ApplicationWithoutNameException(e)
    } catch (Throwable thr) {
      log.error("POST:: throwable occurred: " + app.getName(), thr)
      throw new ApplicationException(thr)
    }
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/name/{name}")
  void delete(@PathVariable String account, @PathVariable String name, HttpServletResponse response) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    def application  = getApplication(credentials)
    try {
      application.initialize(new Application().withName(name)).delete()
      response.setStatus(HttpStatus.ACCEPTED.value())
    } catch (NoPrimaryKeyException e) {
      log.info("GET(/name/{name}) -> NoPrimaryKeyException occurred for app: ${name}: ${e.message}")
      throw new ApplicationNotFoundException(e)
    }
  }

  @RequestMapping(method = RequestMethod.GET, value = "/name/{name}")
  Application getByName(@PathVariable String account, @PathVariable final String name) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    def application  = getApplication(credentials)
    try {
      return application.findByName(name)
    } catch (NotFoundException e) {
      log.info("GET(/name/{name}) -> NotFoundException occurred for app: ${name}: ${e.message}")
      throw new ApplicationNotFoundException(e)
    } catch (Throwable thr) {
      log.error("GET(/name/{name}) -> Throwable occurred: ", thr)
      throw new ApplicationException(thr)
    }
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Applications must have a name")
  class ApplicationWithoutNameException extends RuntimeException {
    public ApplicationWithoutNameException(Throwable cause) {
      super(cause)
    }

    public ApplicationWithoutNameException(String message) {
      super(message)
    }
  }

  private Application getApplication(AccountCredentials accountCredentials) {
    def dao = null
    for (daoProvider in applicationDAOProviders) {
      if (daoProvider.supports(accountCredentials.getClass())) {
        dao = daoProvider.getForAccount(accountCredentials)
        break
      }
    }
    dao ? new Application(dao: dao) : null
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Exception, baby")
  class ApplicationException extends RuntimeException {
    public ApplicationException(Throwable cause) {
      super(cause)
    }
  }

  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No applications found")
  class NoApplicationsFoundException extends RuntimeException {
    public NoApplicationsFoundException(Throwable cause) {
      super(cause)
    }
  }

  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Application not found")
  class ApplicationNotFoundException extends RuntimeException {
    public ApplicationNotFoundException(Throwable cause) {
      super(cause)
    }
  }
}
