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

package com.netflix.bluespar.oort.controllers

import com.netflix.bluespar.oort.clusters.Cluster
import com.netflix.bluespar.oort.deployables.Application
import com.netflix.bluespar.oort.deployables.ApplicationProvider
import groovy.transform.Canonical
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

import javax.servlet.http.HttpServletRequest

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

    nameKeyedApplicationList.inject(new HashMap<>()) { Map<String, ApplicationViewModel> viewModels, String name, Application application ->
      if (!viewModels.containsKey(name)) {
        viewModels[name] = convertToModel(name, application)
      }
      application.clusters.list().each { Cluster cluster ->
        viewModels[name].clusters = (viewModels[name].clusters ?: new HashSet()) << cluster.name
        viewModels[name].clusterCount += 1
        viewModels[name].serverGroupCount += cluster.serverGroups?.size()
        viewModels[name].instanceCount += cluster.serverGroups?.collect { it.getInstanceCount() }?.sum() ?: 0
      }
      viewModels
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
    def serverGroupCount = (clusters.collect { it.serverGroups.size() }?.sum() ?: 0) as int
    def instanceCount = clusters.inject(0) { int c, Cluster cluster -> c += (cluster.serverGroups?.collect { it.getInstanceCount() }?.sum() ?: 0); c }

    def model = new ApplicationViewModel(name: name)
    model.clusterCount = clusters.size()
    model.instanceCount = instanceCount
    model.serverGroupCount = serverGroupCount
    model.instanceCount = instanceCount
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
