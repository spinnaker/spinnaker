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

package com.netflix.spinnaker.igor.jenkins

import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

import javax.ws.rs.QueryParam

/**
 * A controller that provides jenkins information
 */
@RestController
@Slf4j
class InfoController {

    @Autowired
    JenkinsCache cache

    @Autowired
    JenkinsMasters masters

    @Autowired
    JenkinsProperties jenkinsProperties

    @RequestMapping(value = '/masters', method = RequestMethod.GET)
    List<Object> listMasters(@RequestParam(value = "showUrl", defaultValue = "false") String showUrl) {
        log.info('Getting list of masters')
        if (showUrl == 'true') {
            jenkinsProperties.masters.collect {
                [
                    "name"   : it.name,
                    "address": it.address
                ]
            }
        } else {
            masters.map.keySet().sort()
        }
    }

    @RequestMapping(value = '/jobs/{master}', method = RequestMethod.GET)
    List<String> getJobs(@PathVariable String master) {
        log.info('Getting list of jobs for master: {}', master)

        def jenkinsService = masters.map[master]
        if (!jenkinsService) {
            throw new MasterNotFoundException("Master '${master}' does not exist")
        }

        jenkinsService.jobs.list.collect { it.name }
    }

    @RequestMapping(value = '/jobs/{master}/{job:.+}')
    JobConfig getJobConfig(@PathVariable String master, @PathVariable String job) {
        log.info('Getting the job config for {} at {}', job, master)

        def jenkinsService = masters.map[master]
        if (!jenkinsService) {
            throw new MasterNotFoundException("Master '${master}' does not exist")
        }

        jenkinsService.getJobConfig(job)
    }

    static class MasterResults {
        String master
        List<String> results = []
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @InheritConstructors
    static class MasterNotFoundException extends RuntimeException {}
}
