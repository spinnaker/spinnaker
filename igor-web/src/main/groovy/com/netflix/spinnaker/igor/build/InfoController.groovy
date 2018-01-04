/*
 * Copyright 2014 Netflix, Inc.
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
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildMasters
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
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

    @RequestMapping(value = '/masters', method = RequestMethod.GET)
    List<Object> listMasters(@RequestParam(value = "showUrl", defaultValue = "false") String showUrl) {
        if (showUrl == 'true') {
            List<Object> masterList = jenkinsProperties?.masters.collect {
                [
                    "name"   : it.name,
                    "address": it.address
                ]
            }
            masterList.addAll(
                travisProperties?.masters.collect {
                    [
                        "name": it.name,
                        "address": it.address
                    ]
                }
            )
            masterList.addAll(
                gitlabCiProperties?.masters.collect {
                    [
                        "name": it.name,
                        "address": it.address
                    ]
                }
            )
            return masterList
        } else {
            return buildMasters.map.keySet().sort()
        }
    }

    @RequestMapping(value = '/jobs/{master:.+}', method = RequestMethod.GET)
    List<String> getJobs(@PathVariable String master) {
        def jenkinsService = buildMasters.filteredMap(BuildServiceProvider.JENKINS)[master]
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
            return buildCache.getJobNames(master)
        } else {
            throw new MasterNotFoundException("Master '${master}' does not exist")
        }
    }

    @RequestMapping(value = '/jobs/{master:.+}/**')
    Object getJobConfig(@PathVariable String master, HttpServletRequest request) {
        def job = (String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).split('/').drop(3).join('/')
        def service = buildMasters.map[master]
        if (!service) {
            throw new MasterNotFoundException("Master '${master}' does not exist")
        }
        return service.getJobConfig(job)
    }

    static class MasterResults {
        String master
        List<String> results = []
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @InheritConstructors
    static class MasterNotFoundException extends RuntimeException {}
}
