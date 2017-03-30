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
import spock.lang.Specification
import spock.lang.Unroll

class ConfigurableFileDecoratorSpec extends Specification {
    @Unroll
    def "decorate artifacts using regex groups"() {
        when:
        GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference)
        ConfigurableFileDecorator configurableFileDecorator = new ConfigurableFileDecorator(artifactType, decoratorRegex, decoratorRegex)
        configurableFileDecorator.decorate(genericArtifact)

        then:
        genericArtifact.name      == name
        genericArtifact.version   == version
        genericArtifact.type      == type
        genericArtifact.reference == reference

        where:
        artifactType    | decoratorRegex                                 | reference                || name            | version | type
        "war-release"   | /([a-zA-Z-]+)\-([\d\.]+)\.war$/                | "test-artifact-2.13.war" || "test-artifact" | "2.13"  | "war-release"
        "jar-release"   | /([a-zA-Z-]+)\-([\d\.]+)\.jar$/                | "test-artifact-2.13.jar" || "test-artifact" | "2.13"  | "jar-release"
        "Auto resolved" | /([a-zA-Z-]+)\-([\d\.]+)\.([jw]ar)$/           | "test-artifact-2.13.war" || "test-artifact" | "2.13"  | "war"
        "snapshot-java" |
            /[\/\:.]*\/([a-zA-Z-]+)\-([\d\.]+\-[\d\.]+)[a-z\-\d]+\.jar$/ |
            "http://m.a.v/e/n/r/e/p/o/test-artifact/1.3.37-SNAPSHOT/test-artifact-1.3.37-20170304.121217-1337-shaded.jar" ||
            "test-artifact" |
            "1.3.37-20170304.121217" |
            "snapshot-java"
    }

    @Unroll
    def "identify artifacts using regex"() {
        when:
        GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference)
        ConfigurableFileDecorator configurableFileDecorator = new ConfigurableFileDecorator("dummy", identifier, identifier)
        configurableFileDecorator.decorate(genericArtifact)

        then:
        configurableFileDecorator.handles(genericArtifact) == knownArtifact

        where:
        artifactType    | identifier                            | reference                || knownArtifact
        "war-release"   | /([a-zA-Z-]+)\-([\d\.]+)\.war$/       | "test-artifact-2.13.war" || true
        "jar-release"   | /([a-zA-Z-]+)\-([\d\.]+)\.jar$/       | "test-artifact-2.13.jar" || true
        "jar-release"   | /([a-zA-Z-]+)\-([\d\.]+)\.jar$/       | "rosco_0.35.0-3_all.deb" || false
        "Auto resolved" | /([a-zA-Z-]+)\-([\d\.]+)\.([jw]ar)$/  | "test-artifact-2.13.war" || true
    }
}
