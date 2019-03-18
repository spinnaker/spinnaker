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

package com.netflix.spinnaker.igor.jenkins.client.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.netflix.spinnaker.igor.build.model.GenericArtifact
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.Result
import groovy.transform.CompileStatic

import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/**
 * Represents a build in Jenkins
 */
@CompileStatic
@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlRootElement
class Build {
    boolean building
    Integer number
    @XmlElement(required = false)
    String result
    String timestamp
    @XmlElement(required = false)
    Long duration
    @XmlElement(required = false)
    Integer estimatedDuration
    @XmlElement(required = false)
    String id
    String url
    @XmlElement(required = false)
    String builtOn
    @XmlElement(required = false)
    String fullDisplayName

    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "artifact", required = false)
    List<BuildArtifact> artifacts

    /*
    We need to dump this into a list first since the Jenkins query returns
    multiple action elements, with all but the test run one empty.  We then filter it into a testResults var
     */
    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "action", required = false)
    List<TestResults> testResults

    GenericBuild genericBuild(String jobName) {
        GenericBuild genericBuild = new GenericBuild(building: building, number: number.intValue(), duration: duration.intValue(), result: result as Result, name: jobName, url: url, timestamp: timestamp, fullDisplayName: fullDisplayName)
        if (artifacts) {
            genericBuild.artifacts = artifacts.collect { buildArtifact ->
                GenericArtifact artifact = buildArtifact.getGenericArtifact()
                artifact.name = jobName
                artifact.version = number
                artifact
            }
        }
        if (testResults) {
            genericBuild.testResults = testResults
        }
        return genericBuild
    }
}
