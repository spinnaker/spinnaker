/*
 * Copyright 2014 Netflix, Inc.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.build

import com.netflix.spinnaker.igor.config.ConcourseProperties
import com.netflix.spinnaker.igor.config.GitlabCiProperties
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.config.TravisProperties
import com.netflix.spinnaker.igor.config.WerckerProperties
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildService
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.wercker.WerckerService
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping

import javax.servlet.http.HttpServletRequest

/**
 * A controller that provides jenkins information
 */
@RestController
@Slf4j
class InfoController {

    @Autowired
    BuildCache buildCache

    @Autowired(required = false)
    JenkinsProperties jenkinsProperties

    @Autowired
    BuildServices buildServices

    @Autowired(required = false)
    TravisProperties travisProperties

    @Autowired(required = false)
    GitlabCiProperties gitlabCiProperties

    @Autowired(required = false)
    WerckerProperties werckerProperties

    @Autowired(required = false)
    ConcourseProperties concourseProperties

    @RequestMapping(value = '/masters', method = RequestMethod.GET)
    @PostFilter("hasPermission(filterObject, 'BUILD_SERVICE', 'READ')")
    List<String> listMasters(@RequestParam(value = "type", defaultValue = "") String type) {

        BuildServiceProvider providerType = (type == "") ? null : BuildServiceProvider.valueOf(type.toUpperCase())
        //Filter by provider type if it is specified
        if (providerType) {
            return buildServices.getServiceNames(providerType)
        } else {
            return buildServices.getServiceNames()
        }
    }

    @RequestMapping(value = '/buildServices', method = RequestMethod.GET)
    List<BuildService> getAllBuildServices() {
        buildServices.allBuildServices
    }

    @RequestMapping(value = '/jobs/{master:.+}', method = RequestMethod.GET)
    @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
    List<String> getJobs(@PathVariable String master) {
        def buildService = buildServices.getService(master)
        if (buildService == null) {
            throw new NotFoundException("Master '${master}' does not exist")
        }

        if (buildService instanceof JenkinsService) {
            JenkinsService jenkinsService = (JenkinsService) buildService
            def jobList = []
            def recursiveGetJobs

            recursiveGetJobs = { list, prefix = "" ->
                if (prefix) {
                    prefix = prefix + "/job/"
                }
                list.each {
                    if (it.list == null || it.list.empty) {
                        jobList << prefix + it.name
                    } else {
                        recursiveGetJobs(it.list, prefix + it.name)
                    }
                }
            }
            recursiveGetJobs(jenkinsService.jobs.list)

            return jobList
        } else if (buildService instanceof WerckerService) {
            WerckerService werckerService = (WerckerService) buildService
            return werckerService.getJobs()
        } else {
            return buildCache.getJobNames(master)
        }
    }

    @RequestMapping(value = '/jobs/{master:.+}/**')
    @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
    Object getJobConfig(@PathVariable String master, HttpServletRequest request) {
        def job = (String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).split('/').drop(3).join('/')
        def service = buildServices.getService(master)
        if (service == null) {
            throw new NotFoundException("Master '${master}' does not exist")
        }
        return service.getJobConfig(job)
    }

}
