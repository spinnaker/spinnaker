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
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
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
import org.springframework.web.bind.annotation.RequestParam
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
    Build build(@PathVariable("name") String master, @PathVariable String job,  @RequestParam Map<String,String> requestParams) {
        if (!masters.map.containsKey(master)) {
            throw new MasterNotFoundException()
        }
        try {
            def poller = new BuildJobPoller(job, masters.map[master], requestParams)
            executor.submit(poller).get(30, TimeUnit.SECONDS)
            poller.build
        } catch (RuntimeException e) {
            log.error("Unable to build job `${job}`", e)
            throw e
        }
    }

    @RequestMapping(value = '/jobs/{master}/{job}/{buildNumber}/properties/{fileName:.+}')
    Map<String, String> getProperties(@PathVariable String master, @PathVariable String job, @PathVariable Integer buildNumber, @PathVariable String fileName) {
        if (!masters.map.containsKey(master)) {
            throw new MasterNotFoundException()
        }
        Map<String, String> map = [:]
        try {
            Properties properties = new Properties()
            JenkinsClient jenkinsClient = masters.map[master]
            String path = jenkinsClient.getBuild(job, buildNumber).artifacts.find{ it.fileName == fileName }?.relativePath
            properties.load(jenkinsClient.getPropertyFile(job, buildNumber, path).body.in())
            map = map << properties
        } catch( e ){
            log.error("Unable to get properties `${job}`", e)
        }
        map
    }

    static class BuildJobPoller implements Runnable {
        private final String job
        private final JenkinsClient client
        private final Map<String,String> requestParams
        
        private Build build;

        BuildJobPoller(String job, JenkinsClient client) {
            this.job = job
            this.client = client
        }
        
        BuildJobPoller(String job, JenkinsClient client, Map<String,String> requestParams) {
            this.job = job
            this.client = client
            this.requestParams = requestParams
        }

        void run() {
            def response
            // fetch the build configuration and make sure that it's configured as we expect
            JobConfig jobConfig = client.getJobConfig(job)

            if(requestParams && jobConfig.parameterDefinitionList?.size() > 0) {
                response = client.buildWithParameters(job, requestParams)
            } else if(!requestParams && jobConfig.parameterDefinitionList?.size() > 0) {
                // account for when you just want to fire a job with the default parameter values by adding a dummy param
                response = client.buildWithParameters(job, ['startedBy' : "igor"])
            } else if(!requestParams && (!jobConfig.parameterDefinitionList || jobConfig.parameterDefinitionList.size() == 0)){
                response = client.build(job)
            } else { // Jenkins will reject the build, so don't even try
                log.error("job : ${job}, passing params to a job which doesn't need them")
                // we should throw a BuildJobError, but I get a bytecode error : java.lang.VerifyError: Bad <init> method call from inside of a branch
                throw new RuntimeException()
            }

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
