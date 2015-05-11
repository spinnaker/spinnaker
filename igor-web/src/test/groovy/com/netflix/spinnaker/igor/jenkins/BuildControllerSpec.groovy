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

import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.ParameterDefinition
import retrofit.client.Header
import retrofit.client.Response
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Tests for BuildController
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class BuildControllerSpec extends Specification {

    JenkinsCache cache = Mock(JenkinsCache)
    JenkinsClient client = Mock(JenkinsClient)
    BuildController controller

    final MASTER = 'MASTER'
    final HTTP_201 = 201
    final BUILD_NUMBER = 123
    final QUEUED_JOB_NUMBER = 123456
    final JOB_NAME = "job1"

    void setup() {
        controller = new BuildController(executor: Executors.newSingleThreadExecutor(), masters: new JenkinsMasters(map: [MASTER: client]))
    }

    void 'trigger a build without parameters'() {
        given:
        1 * client.getJobConfig(JOB_NAME) >> new JobConfig()
        1 * client.build(JOB_NAME) >> new Response("http://test.com", HTTP_201, "", [new Header("Location","foo/${QUEUED_JOB_NUMBER}")], null)

        expect:
        controller.build(MASTER,JOB_NAME,null) == QUEUED_JOB_NUMBER.toString()

    }

    void 'trigger a build with parameters to a job with parameters'() {
        given:
        1 * client.getJobConfig(JOB_NAME) >> new JobConfig(parameterDefinitionList: [new ParameterDefinition(defaultName: "name", defaultValue: null, description: "description")])
        1 * client.buildWithParameters(JOB_NAME,[name:"myName"]) >> new Response("http://test.com", HTTP_201, "", [new Header("Location","foo/${QUEUED_JOB_NUMBER}")], null)

        expect:
        controller.build(MASTER,JOB_NAME, [name:"myName"]) == QUEUED_JOB_NUMBER.toString()
    }

    void 'trigger a build without parameters to a job with parameters with default values'() {
        given:
        1 * client.getJobConfig(JOB_NAME) >> new JobConfig(parameterDefinitionList: [new ParameterDefinition(defaultName: "name", defaultValue: "value", description: "description")])
        1 * client.buildWithParameters(JOB_NAME, ['startedBy' : "igor"]) >> new Response("http://test.com", HTTP_201, "", [new Header("Location","foo/${QUEUED_JOB_NUMBER}")], null)

        expect:
        controller.build(MASTER, JOB_NAME, null)  == QUEUED_JOB_NUMBER.toString()
    }

    void 'trigger a build with parameters to a job without parameters'() {
        given:
        1 * client.getJobConfig(JOB_NAME) >> new JobConfig()

        when:
        controller.build(MASTER, JOB_NAME, [foo:"bar"])

        then:
        thrown(RuntimeException)
    }
}
