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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty('artifact.decorator.enabled')
class RpmDetailsDecorator implements ArtifactDetailsDecorator {

    String packageType = 'rpm'
    String versionDelimiter = '-'

    @Override
    GenericArtifact decorate(GenericArtifact genericArtifact) {
        genericArtifact.name = extractName(genericArtifact.fileName)
        genericArtifact.version = extractVersion(genericArtifact.fileName)
        genericArtifact.type = packageType
        genericArtifact.reference = genericArtifact.fileName
        return genericArtifact
    }

    @Override
    boolean handles(GenericArtifact genericArtifact) {
        if (!genericArtifact.fileName) {
            return false
        }
        return genericArtifact.fileName.tokenize('.').last() == "rpm"
    }

    @Override
    String decoratorName() {
        return packageType
    }

    String extractVersion(String file) {
        List<String> parts = file.tokenize(versionDelimiter)
        String suffix = parts.removeLast().replaceAll(".rpm", '')
        return parts.removeLast() + versionDelimiter + suffix
    }

    String extractName(String file) {
        List<String> parts = file.tokenize(versionDelimiter)
        parts.removeLast()
        parts.removeLast()
        return parts.join(versionDelimiter)
    }
}
