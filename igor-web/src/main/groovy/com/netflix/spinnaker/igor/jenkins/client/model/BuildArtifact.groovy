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

import com.netflix.spinnaker.igor.build.model.GenericArtifact
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/**
 * Represents a build artifact
 */
@CompileStatic
@XmlRootElement(name = 'artifact')
class BuildArtifact {
    @XmlElement(required = false)
    String fileName

    @XmlElement(required = false)
    String displayPath

    @XmlElement(required = false)
    String relativePath

    GenericArtifact getGenericArtifact() {
        GenericArtifact artifact = new GenericArtifact(fileName, displayPath, relativePath)
        artifact.type = 'jenkins/file'
        artifact.reference = relativePath
        return artifact
    }
}
