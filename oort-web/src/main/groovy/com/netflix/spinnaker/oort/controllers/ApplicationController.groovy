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

import com.netflix.spinnaker.oort.applications.Application
import com.netflix.spinnaker.oort.applications.ApplicationProvider
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/applications")
class ApplicationController {

  @Autowired
  MessageSource messageSource

  @Autowired
  List<ApplicationProvider> applicationProviders

  @RequestMapping(method = RequestMethod.GET)
  Map<String, ApplicationViewModel> list() {
    List<Application> applicationProviderResults = applicationProviders.collectMany { it.list() ?: [] }
    def nameKeyedApplicationList = getMergedApplications(applicationProviderResults)
    nameKeyedApplicationList.collectEntries { String name, Application application ->
      [(name): convertToModel(name, application)]
    }
  }

  @RequestMapping(value = "/{name}")
  ApplicationViewModel get(@PathVariable("name") String name) {
    List<Application> applicationProviderResults = applicationProviders.collectMany { [it.get(name)] ?: [] }.findAll { Application application -> application } as List<Application>
    def nameKeyedApplicationList = getMergedApplications(applicationProviderResults)
    if (!nameKeyedApplicationList.containsKey(name)) {
      throw new ApplicationNotFoundException(name)
    }
    def viewModel = convertToModel name, nameKeyedApplicationList[name]
    return viewModel
  }

  @ExceptionHandler(ApplicationNotFoundException)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleApplicationNotFoundException(ApplicationNotFoundException ex) {
    def message = messageSource.getMessage("application.not.found", [ex.name] as String[], LocaleContextHolder.locale)
    [error: "Application not found", messages: [message], status: HttpStatus.NOT_FOUND]
  }

  private static Map<String, Application> getMergedApplications(List<Application> applications) {
    Map<String, Application> map = [:]
    applications.each { Application application ->
      if (map.containsKey(application.name)) {
        Application existing = map[application.name]
        def merged = Application.merge(existing, application)
        map[application.name] = merged
      } else {
        map[application.name] = application
      }
    }
    map
  }

  @Canonical
  static class ApplicationNotFoundException extends RuntimeException {
    String name
  }

  static ApplicationViewModel convertToModel(String name, Application application) {
    def clusters = application.clusters.list()
    def serverGroupCount = (clusters.collect { it.serverGroupCount }?.sum() ?: 0) as int
    def instanceCount = (clusters.collect { it.instanceCount }?.sum() ?: 0) as int

    def model = new ApplicationViewModel(name: name)
    model.clusterCount = clusters.size()
    model.instanceCount = instanceCount
    model.serverGroupCount = serverGroupCount
    model.attributes = application.attributes
    model.clusters = (model.clusters ?: new HashSet())
    model.clusters.addAll(clusters.collect { it.name })
    model
  }

  static class ApplicationViewModel {
    String name
    int clusterCount
    int instanceCount
    int serverGroupCount
    Map attributes
    Set<String> clusters
  }
}
