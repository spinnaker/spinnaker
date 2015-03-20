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

import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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

    @RequestMapping(value = '/masters', method = RequestMethod.GET)
    List<String> listMasters() {
        log.info('Getting list of masters')
        masters.map.keySet().sort()
    }

    @RequestMapping(value = '/jobs/{master}', method = RequestMethod.GET)
    List<String> getJobs(@PathVariable String master) {
        log.info('Getting list of jobs for master: {}', master)
        masters.map[master].jobs.list.collect{it.name}
    }

    @RequestMapping(value = '/typeahead')
    List<MasterResults> typeaheadResults(
        @RequestParam String q, @RequestParam(value = 'size', defaultValue = '10') Integer size) {

        log.info('Getting typeahead results for {}, size: {}', q, size)
        Map<String, MasterResults> results = [:]
        List fromCache = cache.getTypeaheadResults(q)
        log.info('Found {} results', fromCache ? fromCache.size() : 0)
        Integer maxResults = size ?: 10
        Integer resultSize = Math.min(fromCache.size(), maxResults)
        List toReturn = resultSize ? fromCache[0..resultSize - 1] : []
        toReturn.each { String key ->
            def masterAndJob = key.split(':'),
                master = masterAndJob[0],
                job = masterAndJob[1]
            if (!results[master]) {
                results.put(master, new MasterResults(master: master))
            }
            results[master].results << job
        }
        results.values().sort { it.master }
    }

    @RequestMapping(value = '/jobs/{master}/{job}')
    JobConfig getJobConfig(@PathVariable String master, @PathVariable String job) {
        log.info('Getting the job config for {} at {}', job, master)
        masters.map[master].getJobConfig(job)
    }

    static class MasterResults {
        String master
        List<String> results = []
    }
}
