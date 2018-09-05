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

import com.netflix.spinnaker.igor.config.GitlabCiProperties
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.config.TravisProperties
import com.netflix.spinnaker.igor.config.WerckerProperties
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
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
    BuildMasters buildMasters

    @Autowired(required = false)
    TravisProperties travisProperties

    @Autowired(required = false)
    GitlabCiProperties gitlabCiProperties

    @Autowired(required = false)
    WerckerProperties werckerProperties

    @RequestMapping(value = '/masters', method = RequestMethod.GET)
    List<Object> listMasters(
        @RequestParam(value = "showUrl", defaultValue = "false") String showUrl,
        @RequestParam(value = "type", defaultValue = "") String type) {

        BuildServiceProvider providerType = (type == "") ? null :
            BuildServiceProvider.valueOf(type.toUpperCase())

        if (showUrl == 'true') {
            List<Object> masterList = []
            addMaster(masterList, providerType, jenkinsProperties,  BuildServiceProvider.JENKINS)
            addMaster(masterList, providerType, travisProperties,   BuildServiceProvider.TRAVIS)
            addMaster(masterList, providerType, gitlabCiProperties, BuildServiceProvider.GITLAB_CI)
            addMaster(masterList, providerType, werckerProperties,  BuildServiceProvider.WERCKER)
            return masterList
        } else {
            //Filter by provider type if it is specified
            if (providerType) {
                return buildMasters.map.findResults {
                    k, v -> v.buildServiceProvider() == providerType ? k : null}.sort()
            } else {
                return buildMasters.map.keySet().sort()
            }
        }
    }
    
    void addMaster(masterList, providerType, properties, expctedType) {
        if (!providerType || providerType == expctedType) {
            masterList.addAll(
                properties?.masters.collect {
                    [
                        "name"         : it.name,
                        "address"      : it.address
                    ]
                }
            )
        }
    }

    @RequestMapping(value = '/jobs/{master:.+}', method = RequestMethod.GET)
    List<String> getJobs(@PathVariable String master) {
        def jenkinsMap = buildMasters.filteredMap(BuildServiceProvider.JENKINS)
        def jenkinsService = jenkinsMap? jenkinsMap[master] : null
        if (jenkinsService) {
            def jobList = []
            def recursiveGetJobs

            recursiveGetJobs = { list, prefix="" ->
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
        } else if (buildMasters.map.containsKey(master)) {
            def werckerMap = buildMasters.filteredMap(BuildServiceProvider.WERCKER)
            def werckerService = werckerMap? werckerMap[master] : null
            if (werckerService) {
                return werckerService.getJobs()
            } else {
                return buildCache.getJobNames(master)
            }
        } else {
            throw new NotFoundException("Master '${master}' does not exist")
        }
    }

    @RequestMapping(value = '/jobs/{master:.+}/**')
    Object getJobConfig(@PathVariable String master, HttpServletRequest request) {
        def job = (String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).split('/').drop(3).join('/')
        def service = buildMasters.map[master]
        if (!service) {
            throw new NotFoundException("Master '${master}' does not exist")
        }
        return service.getJobConfig(job)
    }
}
