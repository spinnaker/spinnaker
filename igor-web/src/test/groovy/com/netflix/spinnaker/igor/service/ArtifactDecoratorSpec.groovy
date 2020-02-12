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

package com.netflix.spinnaker.igor.service

import com.netflix.spinnaker.igor.build.artifact.decorator.ArtifactDetailsDecorator
import com.netflix.spinnaker.igor.build.artifact.decorator.DebDetailsDecorator
import com.netflix.spinnaker.igor.build.artifact.decorator.RpmDetailsDecorator
import com.netflix.spinnaker.igor.build.model.GenericArtifact
import com.netflix.spinnaker.igor.config.ArtifactDecorationProperties
import spock.lang.Specification
import spock.lang.Unroll

class ArtifactDecoratorSpec extends Specification {

    @Unroll
    def "Decorate"() {
        given:
        List<ArtifactDetailsDecorator> artifactDetailsDecorators = new ArrayList<>()
        artifactDetailsDecorators.add (new DebDetailsDecorator())
        artifactDetailsDecorators.add (new RpmDetailsDecorator())
        def regex = /([a-zA-Z-]+)\-([\d\.]+)\.([jw]ar)$/
        ArtifactDecorationProperties.FileDecorator fileDecorator = new ArtifactDecorationProperties.FileDecorator()
        fileDecorator.type = "java-magic"
        fileDecorator.identifierRegex = regex
        fileDecorator.decoratorRegex = regex
        ArtifactDecorationProperties artifactDecorationProperties = new ArtifactDecorationProperties()
        artifactDecorationProperties.fileDecorators = [fileDecorator]
        ArtifactDecorator artifactDecorator = new ArtifactDecorator(artifactDetailsDecorators, artifactDecorationProperties)

        when:
        GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference)
        genericArtifact = artifactDecorator.decorate(genericArtifact)?.first()

        then:
        genericArtifact.name    == name
        genericArtifact.version == version
        genericArtifact.type    == type

        where:
        reference                                   || name               | version                | type
        "openmotif22-libs-2.2.4-192.1.3.x86_64.rpm" || "openmotif22-libs" | "2.2.4-192.1.3.x86_64" | "rpm"
        "api_1.1.1-h01.sha123_all.deb"              || "api"              | "1.1.1-h01.sha123"     | "deb"
        "test-2.13.jar"                             || "test"             | "2.13"                 | "jar"
        "wara-2.13.war"                             || "wara"             | "2.13"                 | "war"
        "unknown-3.2.1.apk"                         || null               | null                   | null
        null                                        || null               | null                   | null
    }

    @Unroll
    def "Override the included parser with a parser defined in configuration"() {
        given:
        List<ArtifactDetailsDecorator> artifactDetailsDecorators = new ArrayList<>()
        artifactDetailsDecorators.add (new DebDetailsDecorator())
        artifactDetailsDecorators.add (new RpmDetailsDecorator())
        def regex = /([a-zA-Z-]+)\_([\d\.]+)\.deb$/
        ArtifactDecorationProperties.FileDecorator fileDecorator = new ArtifactDecorationProperties.FileDecorator()
        fileDecorator.type = "deb-override"
        fileDecorator.identifierRegex = regex
        fileDecorator.decoratorRegex = regex
        ArtifactDecorationProperties artifactDecorationProperties = new ArtifactDecorationProperties()
        artifactDecorationProperties.fileDecorators = [fileDecorator]
        ArtifactDecorator artifactDecorator = new ArtifactDecorator(artifactDetailsDecorators, artifactDecorationProperties)

        when:
        GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference)
        genericArtifact = artifactDecorator.decorate(genericArtifact).first()

        then:
        genericArtifact.name    == name
        genericArtifact.version == version
        genericArtifact.type    == type

        where:
        reference                      || name   | version            | type
        "api_1.1.1-h01.sha123_all.deb" || "api"  | "1.1.1-h01.sha123" | "deb"
        "api_1.1.1.deb"                || "api"  | "1.1.1"            | "deb-override"
    }

    def "should support multiple configurable parsers"() {
        given:
        List<ArtifactDetailsDecorator> artifactDetailsDecorators = new ArrayList<>()
        artifactDetailsDecorators.add(new DebDetailsDecorator())
        artifactDetailsDecorators.add(new RpmDetailsDecorator())
        def oldDockerDecorator = new ArtifactDecorationProperties.FileDecorator()
        oldDockerDecorator.type = "docker"
        oldDockerDecorator.identifierRegex = /(.+\/.+:.+)/
        oldDockerDecorator.decoratorRegex = /[a-zA-Z0-9.]+\/(.+):(.+)/
        def newDockerDecorator = new ArtifactDecorationProperties.FileDecorator()
        newDockerDecorator.type = "docker/image"
        newDockerDecorator.identifierRegex = /(.+\/.+:.+)/
        newDockerDecorator.decoratorRegex = /(.+):(.+)/
        ArtifactDecorationProperties artifactDecorationProperties = new ArtifactDecorationProperties()
        artifactDecorationProperties.fileDecorators = [newDockerDecorator, oldDockerDecorator]
        ArtifactDecorator artifactDecorator = new ArtifactDecorator(artifactDetailsDecorators, artifactDecorationProperties)

        when:
        def reference = "gcr.io/my-images/nginx:0cce25b9a55"
        GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference)
        def genericArtifacts = artifactDecorator.decorate(genericArtifact)

        then:
        genericArtifacts*.name == ["gcr.io/my-images/nginx", "my-images/nginx"]
        genericArtifacts*.version == ["0cce25b9a55", "0cce25b9a55"]
        genericArtifacts*.type == ["docker/image", "docker"]
    }

    def "should only decorate artifacts once"() {
        given:
        List<ArtifactDetailsDecorator> artifactDetailsDecorators = new ArrayList<>()
        artifactDetailsDecorators.add(new DebDetailsDecorator())
        artifactDetailsDecorators.add(new RpmDetailsDecorator())
        def oldDockerDecorator = new ArtifactDecorationProperties.FileDecorator()
        oldDockerDecorator.type = "docker"
        oldDockerDecorator.identifierRegex = /(.+\/.+:.+)/
        oldDockerDecorator.decoratorRegex = /[a-zA-Z0-9.]+\/(.+):(.+)/
        def newDockerDecorator = new ArtifactDecorationProperties.FileDecorator()
        newDockerDecorator.type = "docker/image"
        newDockerDecorator.identifierRegex = /(.+\/.+:.+)/
        newDockerDecorator.decoratorRegex = /(.+):(.+)/
        ArtifactDecorationProperties artifactDecorationProperties = new ArtifactDecorationProperties()
        artifactDecorationProperties.fileDecorators = [newDockerDecorator, oldDockerDecorator]
        ArtifactDecorator artifactDecorator = new ArtifactDecorator(artifactDetailsDecorators, artifactDecorationProperties)

        when:
        def reference = "gcr.io/my-images/nginx:0cce25b9a55"
        GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference)
        def genericArtifacts = artifactDecorator.decorate(genericArtifact)
        genericArtifacts = genericArtifacts.collectMany { artifactDecorator.decorate(it) }

        then:
        genericArtifacts*.name == ["gcr.io/my-images/nginx", "my-images/nginx"]
        genericArtifacts*.version == ["0cce25b9a55", "0cce25b9a55"]
        genericArtifacts*.type == ["docker/image", "docker"]
    }
}
