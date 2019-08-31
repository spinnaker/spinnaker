/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.manifests.kustomize

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization
import com.netflix.spinnaker.rosco.services.ClouddriverService
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification

class KustomizationFileReaderSpec extends Specification {

    def "getKustomization returns a Kustomization object"() {
        given:
        def kustomizationYaml = """
        resources:
        - deployment.yml
        - service.yml
        
        namePrefix: demo-
        """
        def clouddriverService = Mock(ClouddriverService)
        def kustomizationFileReader = new KustomizationFileReader(clouddriverService)
        def baseArtifact = Artifact.builder()
            .name("base")
            .reference("https://api.github.com/repos/org/repo/contents/base/")
            .artifactAccount("test1")
            .type("github/file")
            .build()

        when:
        Kustomization k = kustomizationFileReader.getKustomization(baseArtifact,"kustomization.yml")

        then:
        1 * clouddriverService.fetchArtifact(_ as Artifact) >> { Artifact a ->
            return new Response("test", 200, "", [], new TypedString(kustomizationYaml.stripMargin()))
        }
        k.getResources().sort() == ["deployment.yml", "service.yml"].sort()
        k.getSelfReference() == "https://api.github.com/repos/org/repo/contents/base/kustomization.yml"
    }

    def "getKustomization throws an exception if it can't find a valid kustomization file"() {
        given:
        def clouddriverService = Mock(ClouddriverService) {
            fetchArtifact(_) >> {
                return new Response("test", 500, "", [], null)
            }
        }
        def kustomizationFileReader = new KustomizationFileReader(clouddriverService)
        def invalidArtifact = Artifact.builder()
            .reference("https://api.github.com/repos/org/repo/contents/production")
            .build()

        when:
        kustomizationFileReader.getKustomization(invalidArtifact, "kustomization.yml")

        then:
        IllegalArgumentException ex = thrown()
        ex != null
    }
}
