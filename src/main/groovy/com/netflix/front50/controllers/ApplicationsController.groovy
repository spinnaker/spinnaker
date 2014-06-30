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

package com.netflix.front50.controllers

import com.netflix.front50.exception.NoPrimaryKeyException
import com.netflix.front50.exception.NotFoundException
import com.netflix.front50.model.application.Application
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

import javax.servlet.http.HttpServletResponse

@Slf4j
@RestController
@RequestMapping("/applications")
public class ApplicationsController extends SpringBootServletInitializer {

  @Autowired
  Application application

  @RequestMapping(method = RequestMethod.GET)
  Collection<Application> applications() {
    try {
      return application.findAll()
    } catch (NotFoundException e) {
      log.error("GET(/applications) -> NotFoundException occurred: ", e)
      throw new NoApplicationsFoundException(e)
    } catch (Throwable thr) {
      log.error("GET(/applications) -> Throwable occurred: ", thr)
      throw new ApplicationException(thr)
    }
  }

  @RequestMapping(method = RequestMethod.PUT)
  Application put(@RequestBody final Application app) {
    try {
      if (app.getName() == null || app.getName().equals("")) {
        throw new ApplicationWithoutNameException("Application must have a name")
      }
      final Application foundApp = application.findByName(app.getName())
      application.initialize(foundApp).withName(app.getName()).update(app.allSetColumnProperties())
      return application
    } catch (NotFoundException e) {
      log.error("PUT::App not found: " + app.getName(), e)
      throw new ApplicationNotFoundException(e)
    }
  }

  @RequestMapping(method = RequestMethod.POST, value = "/name/{name}")
  Application post(@RequestBody final Application app) {
    try {
      return application.initialize(app).withName(app.getName()).save()
    } catch (NoPrimaryKeyException e) {
      log.error("POST:: cannot create app as name and/or email is missing: " + app, e)
      throw new ApplicationWithoutNameException(e)
    } catch (Throwable thr) {
      log.error("POST:: throwable occurred: " + app.getName(), thr)
      throw new ApplicationException(thr)
    }
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/name/{name}")
  void delete(@PathVariable final String name, HttpServletResponse response) {
    try {
      application.initialize(new Application().withName(name)).delete()
      response.sendError HttpStatus.ACCEPTED.value()
    } catch (NoPrimaryKeyException e) {
      log.error("GET(/name/{name}) -> NotFoundException occurred for app: " + name, e)
      throw new ApplicationNotFoundException(e)
    }
  }

  @RequestMapping(method = RequestMethod.GET, value = "/name/{name}")
  Application getByName(@PathVariable final String name) {
    try {
      return application.findByName(name)
    } catch (NotFoundException e) {
      log.error("GET(/name/{name}) -> NotFoundException occurred for app: " + name, e)
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
