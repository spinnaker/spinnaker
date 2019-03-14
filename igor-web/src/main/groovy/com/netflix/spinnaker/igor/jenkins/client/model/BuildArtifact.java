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

package com.netflix.spinnaker.igor.jenkins.client.model;

import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import lombok.Data;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "artifact")
@Data
public class BuildArtifact {
    @XmlElement
    String fileName;

    @XmlElement
    String displayPath;

    @XmlElement
    String relativePath;

    String type = "jenkins/file";

    String reference;

    String name;

    Integer version;

    GenericArtifact getGenericArtifact() {
        return new GenericArtifact(fileName, displayPath, relativePath);
    }
}
