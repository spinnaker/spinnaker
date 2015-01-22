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

import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import org.simpleframework.xml.Transient

/**
 * Represents a build in Jenkins
 */
@Default
@CompileStatic
@Root(strict = false)
class Build {
    boolean building
    Integer number
    @Element(required = false)
    String result
    String timestamp
    @Element(required = false)
    Integer duration
    @Element(required = false)
    Integer estimatedDuration
    @Element(required = false)
    String id
    String url
    @Element(required = false)
    String builtOn
    @ElementList(required = false, name="artifact", inline = true)
    List<BuildArtifact> artifacts
    /*
    We need to dump this into a list first since the Jenkins query returns
    multiple action elements, with all but the test run one empty.  We then filter it into a testResults var
     */
    @Element(required = false, name="action")
    TestResults testResults
}



