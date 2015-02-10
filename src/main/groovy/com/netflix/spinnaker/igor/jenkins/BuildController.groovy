/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.igor.jenkins

import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

@Slf4j
@RestController
class BuildController {
    @Autowired
    JenkinsMasters masters

    @Autowired
    ExecutorService executor

    @RequestMapping(value = '/jobs/{master}/{job}/{buildNumber}')
    Build getJobStatus(@PathVariable String master, @PathVariable String job, @PathVariable Integer buildNumber) {
        if (!masters.map.containsKey(master)) {
            throw new MasterNotFoundException()
        }
        masters.map[master].getBuild(job, buildNumber)
    }

    @RequestMapping(value = '/masters/{name}/jobs/{job}', method = RequestMethod.PUT)
    Build build(@PathVariable("name") String master, @PathVariable String job) {
        if (!masters.map.containsKey(master)) {
            throw new MasterNotFoundException()
        }
        try {
            def poller = new BuildJobPoller(job, masters.map[master])
            executor.submit(poller).get(30, TimeUnit.SECONDS)
            poller.build
        } catch (RuntimeException e) {
            log.error("Unable to build job `${job}`", e)
            throw e
        }
    }

    static class BuildJobPoller implements Runnable {
        private final String job
        private final JenkinsClient client

        private Build build;

        BuildJobPoller(String job, JenkinsClient client) {
            this.job = job
            this.client = client
        }

        void run() {
            def response = client.build(job)
            if (response.status != 201) {
                throw new BuildJobError()
            }

            log.info("Submitted build job `${job}`")
            def locationHeader = response.headers.find { it.name == "Location" }
            if (!locationHeader) {
                throw new QueuedJobDeterminationError()
            }
            def queuedLocation = locationHeader.value
            def item = queuedLocation.split('/')[-1].toInteger()

            log.info("Polling for queued job item `${job}:${item}`")
            while (true) {
                try {
                    def queue = client.getQueuedItem(item)
                    if (queue && queue.number) {
                        this.build = client.getBuild(job, queue.number)
                        log.info("Found build for queued job item `${job}:${item}`")
                        break
                    }
                    sleep 500
                } catch (RetrofitError e) {
                    log.error("Failed to get build for job `${job}`", e)
                    throw new QueuedJobDeterminationError()
                }
            }
        }

        public Build getBuild() {
            this.build
        }
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Jenkins master not found!")
    @InheritConstructors
    static class MasterNotFoundException extends RuntimeException {}

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Build job failed!")
    @InheritConstructors
    static class BuildJobError extends RuntimeException {}

    @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Failed to determine job from queued item!")
    @InheritConstructors
    static class QueuedJobDeterminationError extends RuntimeException {}
}
