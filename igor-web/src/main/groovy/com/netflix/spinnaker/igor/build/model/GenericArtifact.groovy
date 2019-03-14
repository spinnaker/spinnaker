/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.build.model

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.ToString

@ToString(includeNames = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class GenericArtifact {
    String fileName
    String displayPath
    String relativePath
    String reference
    String name
    String type
    String version

    Map<String, String> metadata

    GenericArtifact(String fileName, String displayPath, String relativePath) {
        this.fileName = fileName
        this.displayPath = displayPath
        this.relativePath = relativePath
    }

    GenericArtifact(String type, String name, String version, String reference) {
        this.type = type
        this.name = name
        this.version = version
        this.reference = reference
    }

}
