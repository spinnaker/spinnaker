/*
 * Copyright 2017 Schibsted ASA.
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

package com.netflix.spinnaker.igor.build.artifact.decorator

import com.netflix.spinnaker.igor.build.model.GenericArtifact
import org.codehaus.groovy.runtime.InvokerHelper

class ConfigurableFileDecorator implements ArtifactDetailsDecorator {

    final String decoratorRegex
    final String identifierRegex
    final String type

    ConfigurableFileDecorator(String type, String decoratorRegex, String identifierRegex) {
        this.decoratorRegex = decoratorRegex
        this.identifierRegex = identifierRegex
        this.type = type
    }

    @Override
    GenericArtifact decorate(GenericArtifact genericArtifact) {
        GenericArtifact copy = new GenericArtifact()
        InvokerHelper.setProperties(copy, genericArtifact.properties)

        def m

        if ((m = genericArtifact.fileName =~ decoratorRegex)) {
            copy.name = m.group(1)
            copy.version = m.group(2)
            copy.type = m.groupCount() > 2 ? m.group(3) : type
            copy.reference = genericArtifact.fileName
        }

        return copy
    }

    @Override
    boolean handles(GenericArtifact genericArtifact) {
        return genericArtifact.fileName =~ identifierRegex
    }

    @Override
    String decoratorName() {
        return type
    }
}
