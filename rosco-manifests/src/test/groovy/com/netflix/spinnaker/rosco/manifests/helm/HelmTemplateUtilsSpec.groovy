/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.rosco.manifests.helm

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.rosco.jobs.BakeRecipe
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties
import spock.lang.Specification

class HelmTemplateUtilsSpec extends Specification {

    def "removeTestsDirectoryTemplates returns a Kubernetes manifest with the test manifests removed from the input"() {
        given:
        def inputManifests = """
        ---
        # Source: mysql/templates/pvc.yaml
        
        kind: PersistentVolumeClaim
        apiVersion: v1
        metadata:
          name: release-name-mysql
          namespace: default
        spec:
          accessModes:
            - "ReadWriteOnce"
          resources:
            requests:
              storage: "8Gi"
        ---
        # Source: mysql/templates/tests/test-configmap.yaml
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: release-name-mysql-test
          namespace: default
        data:
          run.sh: |-
        """

        def artifactDownloader = Mock(ArtifactDownloader)
        def helmProperties = Mock(RoscoHelmConfigurationProperties)
        def helmTemplateUtils = new HelmTemplateUtils(artifactDownloader, helmProperties)

        when:
        def output = helmTemplateUtils.removeTestsDirectoryTemplates(inputManifests)

        then:
        def expected = """
        ---
        # Source: mysql/templates/pvc.yaml
        
        kind: PersistentVolumeClaim
        apiVersion: v1
        metadata:
          name: release-name-mysql
          namespace: default
        spec:
          accessModes:
            - "ReadWriteOnce"
          resources:
            requests:
              storage: "8Gi"
        """
        output.trim() == expected.trim()
    }

    def "removeTestsDirectoryTemplates returns the input Kubernetes manifest unchanged since no tests are available"() {
        given:
        def inputManifests = """
        ---
        # Source: mysql/templates/pvc.yaml
        
        kind: PersistentVolumeClaim
        apiVersion: v1
        metadata:
          name: release-name-mysql
          namespace: default
        spec:
          accessModes:
            - "ReadWriteOnce"
          resources:
            requests:
              storage: "8Gi"
        ---
        # Source: mysql/templates/configmap.yaml
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: release-name-mysql-test
          namespace: default
        data:
          run.sh: |-
        """

        def artifactDownloader = Mock(ArtifactDownloader)
        def helmProperties = Mock(RoscoHelmConfigurationProperties)
        def helmTemplateUtils = new HelmTemplateUtils(artifactDownloader, helmProperties)

        when:
        def output = helmTemplateUtils.removeTestsDirectoryTemplates(inputManifests)

        then:
        def expected = """
        ---
        # Source: mysql/templates/pvc.yaml
        
        kind: PersistentVolumeClaim
        apiVersion: v1
        metadata:
          name: release-name-mysql
          namespace: default
        spec:
          accessModes:
            - "ReadWriteOnce"
          resources:
            requests:
              storage: "8Gi"
        ---
        # Source: mysql/templates/configmap.yaml
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: release-name-mysql-test
          namespace: default
        data:
          run.sh: |-
        """
        output.trim() == expected.trim()
    }

    def "buildBakeRecipe selects helm executable based on specified version"() {
        given:
        def artifactDownloader = Mock(ArtifactDownloader)
        def helmProperties = Mock(RoscoHelmConfigurationProperties)
        def helmTemplateUtils = new HelmTemplateUtils(artifactDownloader, helmProperties)
        def request = new HelmBakeManifestRequest()
        def artifact = Artifact.builder().build()
        request.inputArtifacts = [artifact]
        request.namespace = "default"
        request.overrides = [:]

        when:
        request.templateRenderer = templateRenderer
        BakeRecipe recipe = BakeManifestEnvironment.create().withCloseable { env ->
            helmTemplateUtils.buildBakeRecipe(env, request)
        }

        then:
        (0..1) * helmProperties.v2ExecutablePath >> "helm2"
        (0..1) * helmProperties.v3ExecutablePath >> "helm3"
        recipe.command[0] == command

        where:
        templateRenderer                                | command
        BakeManifestRequest.TemplateRenderer.HELM2      | "helm2"
        BakeManifestRequest.TemplateRenderer.HELM3      | "helm3"
    }
}
