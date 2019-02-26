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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.view.ApplicationClusterViewModel
import com.netflix.spinnaker.clouddriver.model.view.ApplicationViewModel
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Slf4j
@RestController
@RequestMapping("/applications")
class ApplicationsController {

  @Autowired(required = false)
  List<ApplicationProvider> applicationProviders = []

  @Autowired(required = false)
  List<ClusterProvider> clusterProviders = []

  @Autowired
  MessageSource messageSource

  @Autowired
  RequestQueue requestQueue

  @PreAuthorize("#restricted ? @fiatPermissionEvaluator.storeWholePermission() : true")
  @PostFilter("#restricted ? hasPermission(filterObject.name, 'APPLICATION', 'READ') : true")
  @RequestMapping(method = RequestMethod.GET)
  List<Application> list(@RequestParam(required = false, value = 'expand', defaultValue = 'true') boolean expand,
                         @RequestParam(required = false, value = 'restricted', defaultValue = 'true') boolean restricted) {
    def results = requestQueue.execute("applications", {
      applicationProviders.collectMany { it.getApplications(expand) ?: [] }
    })
    results.removeAll([null])
    results.sort { a, b -> a?.name?.toLowerCase() <=> b?.name?.toLowerCase() }
  }

  @PreAuthorize("hasPermission(#name, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
  ApplicationViewModel get(@PathVariable String name) {
    try {
      def apps = requestQueue.execute(name, {
        applicationProviders.collect { it.getApplication(name) }
      }) - null
      if (!apps) {
        throw new NotFoundException("Application does not exist (name: ${name})")
      } else {
        return transform(apps)
      }
    } catch (e) {
      throw new NotFoundException("Application does not exist (name: ${name})")
    }
  }

  private ApplicationViewModel transform(List<Application> apps) {
    def attributes = [:]
    ApplicationViewModel result = null
    for (Application app in apps) {
      if (!result) {
        result = new ApplicationViewModel(name: app.name, clusters: [:])
      }
      attributes << app.attributes

      clusterProviders.collectMany { provider ->
        requestQueue.execute(app.name, {
          provider.getClusterSummaries(app.name)?.values()?.flatten() as Set ?: []
        })
      }.each { Cluster cluster ->
        def account = cluster.accountName
        if (!result.clusters.containsKey(account)) {
          result.clusters[account] = []
        }
        if (!result.clusters[account].find { it.name == cluster.name }) {
          result.clusters[account] << new ApplicationClusterViewModel(name: cluster.name, loadBalancers: cluster.loadBalancers.name as TreeSet, serverGroups: cluster.serverGroups*.name as TreeSet, provider: cluster.type)
        } else {
          result.clusters[account].loadBalancers.addAll(cluster.loadBalancers*.name)
          result.clusters[account].serverGroups.addAll(cluster.serverGroups*.name)
        }
        if (!attributes.cloudProviders) {
          attributes.cloudProviders = cluster.type
        } else {
          if (!attributes.cloudProviders.split(',').contains(cluster.type)) {
            attributes.cloudProviders += ",${cluster.type}"
          }
        }
      }
    }
    result.attributes = attributes
    result
  }
}
