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
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

/**
 * Represents a Project returned by the Jenkins service in the project list
 */
@CompileStatic
@Root(name='job')
class Project {
    @Element String name
    @Element(required = false)
    Build lastBuild

/*
    @Attribute String webUrl
    @Attribute Integer lastBuildLabel
    @Attribute String lastBuildTime
    @Attribute String lastBuildStatus
    @Attribute(required=false) String activity
    @Attribute(required=false) List<BuildArtifact> artifacts
 */
}
