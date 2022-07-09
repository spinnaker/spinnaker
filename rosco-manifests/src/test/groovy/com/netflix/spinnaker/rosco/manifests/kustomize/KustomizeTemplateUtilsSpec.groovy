package com.netflix.spinnaker.rosco.manifests.kustomize

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.rosco.jobs.BakeRecipe
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest
import com.netflix.spinnaker.rosco.manifests.config.RoscoKustomizeConfigurationProperties
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.ConfigMapGenerator
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization
import spock.lang.Specification

class KustomizeTemplateUtilsSpec extends Specification {

    def "getFilesFromArtifact returns a list of files to download based on a kustomization"() {
        given:
        def referenceBase = "https://api.github.com/repos/org/repo/contents/base"
        def artifactDownloader = Mock(ArtifactDownloader)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizeProperties = Mock(RoscoKustomizeConfigurationProperties)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, artifactDownloader, kustomizeProperties)
        def baseArtifact = Artifact.builder()
                .name("base/kustomization.yml")
                .reference(referenceBase + "/kustomization.yml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        kustomizationFileReader.getKustomization(_, "kustomization.yml") >> {
            return new Kustomization(resources: ["deployment.yml", "service.yml"], selfReference: referenceBase + "/kustomization.yml")
        }
        filesToFetch.sort() == [referenceBase.concat("/deployment.yml"),
                                referenceBase.concat("/kustomization.yml"),
                                referenceBase.concat("/service.yml")].sort()
    }

    def "getFilesFromSubFolder returns a list of files where one of the resources is referencing another kustomization"() {
        given:
        def referenceBase = "https://api.github.com/repos/org/repo/contents/base"
        def artifactDownloader = Mock(ArtifactDownloader)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizeProperties = Mock(RoscoKustomizeConfigurationProperties)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, artifactDownloader, kustomizeProperties)
        def baseArtifact = Artifact.builder()
                .name("base/kustomization.yml")
                .reference(referenceBase + "/kustomization.yml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        // the base artifact supplies deployment.yml, service.yml and production (a subdirectory)
        // production supplies configMap.yml
        kustomizationFileReader.getKustomization(_ as Artifact, _ as String) >> { Artifact a, String s ->
            if (a.getName() == "base") {
                return new Kustomization(resources: ["deployment.yml", "service.yml", "production"], selfReference: referenceBase + "/kustomization.yml")
            } else if (a.getName() == "base/production") {
                return new Kustomization(resources: ["configMap.yml"], selfReference: referenceBase + "/production/kustomization.yml")
            }
        }
        filesToFetch.sort() == [
                referenceBase + "/kustomization.yml",
                referenceBase + "/deployment.yml",
                referenceBase + "/service.yml",
                referenceBase + "/production/kustomization.yml",
                referenceBase + "/production/configMap.yml"
        ].sort()
    }

    def "getFilesFromParent returns a list of files where one of the resources is referencing another kustomization"() {
        given:
        def referenceBase = "https://api.github.com/repos/kubernetes-sigs/kustomize/contents"
        def artifactDownloader = Mock(ArtifactDownloader)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizeProperties = Mock(RoscoKustomizeConfigurationProperties)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, artifactDownloader, kustomizeProperties)
        def baseArtifact = Artifact.builder()
                .name("examples/ldap/overlays/production/kustomization.yaml")
                .reference(referenceBase + "/examples/ldap/overlays/production/kustomization.yaml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        // the base artifact supplies deployment.yml, service.yml and production (a subdirectory)
        // production supplies deployment.yaml, ../../base up two levels and provides a ConfigMapGenerator
        // which supplies a env.startup.txt file
        kustomizationFileReader.getKustomization(_ as Artifact, _ as String) >> { Artifact a, String s ->
            if (a.getName() == "examples/ldap/overlays/production") {
                return new Kustomization(
                        resources: ["../../base"],
                        patchesStrategicMerge: ["deployment.yaml"],
                        selfReference: referenceBase + "/examples/ldap/overlays/production/kustomization.yaml")
            } else if (a.getName() == "examples/ldap/base") {
                return new Kustomization(
                        resources: ["deployment.yaml", "service.yaml"],
                        configMapGenerator: [new ConfigMapGenerator(files: ["env.startup.txt"])],
                        selfReference: referenceBase + "/examples/ldap/base/kustomization.yaml"
                )
            }

        }
        filesToFetch.sort() == [
                referenceBase + "/examples/ldap/overlays/production/deployment.yaml",
                referenceBase + "/examples/ldap/overlays/production/kustomization.yaml",
                referenceBase + "/examples/ldap/base/service.yaml",
                referenceBase + "/examples/ldap/base/deployment.yaml",
                referenceBase + "/examples/ldap/base/kustomization.yaml",
                referenceBase + "/examples/ldap/base/env.startup.txt"
        ].sort()
    }

    def "getFilesFromSameFolder returns a list of files where one of the resources is referencing to a kustomization"() {
        given:
        def referenceBase = "https://api.github.com/repos/kubernetes-sigs/kustomize/contents"
        def artifactDownloader = Mock(ArtifactDownloader)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizeProperties = Mock(RoscoKustomizeConfigurationProperties)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, artifactDownloader, kustomizeProperties)
        def baseArtifact = Artifact.builder()
                .name("examples/helloWorld/kustomization.yaml")
                .reference(referenceBase + "/examples/helloWorld/kustomization.yaml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        // the base artifact supplies deployment.yml, service.yml and configMap.yaml
        kustomizationFileReader.getKustomization(_ as Artifact, _ as String) >> { Artifact a, String s ->
            return new Kustomization(
                    resources: ["deployment.yaml", "service.yaml", "configMap.yaml"],
                    selfReference: referenceBase + "/examples/helloWorld/kustomization.yaml")
        }
        filesToFetch.sort() == [
                referenceBase + "/examples/helloWorld/deployment.yaml",
                referenceBase + "/examples/helloWorld/service.yaml",
                referenceBase + "/examples/helloWorld/configMap.yaml",
                referenceBase + "/examples/helloWorld/kustomization.yaml"
        ].sort()
    }

    def "getFilesFromMixedFolders returns a list of files where one of the resources is referencing another kustomization (5)"() {
        given:
        def referenceBase = "https://api.github.com/repos/kubernetes-sigs/kustomize/contents"
        def artifactDownloader = Mock(ArtifactDownloader)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizeProperties = Mock(RoscoKustomizeConfigurationProperties)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, artifactDownloader, kustomizeProperties)
        def baseArtifact = Artifact.builder()
                .name("examples/multibases/kustomization.yaml")
                .reference(referenceBase + "/examples/multibases/kustomization.yaml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        kustomizationFileReader.getKustomization(_ as Artifact, _ as String) >> { Artifact a, String s ->
            String selfReference = referenceBase + "/${a.getName()}/kustomization.yaml"
            if (a.getName() == "examples/multibases") {
                return new Kustomization(resources: ["dev", "staging", "production"], selfReference: selfReference)
            } else if (a.getName() == "examples/multibases/dev") {
                return new Kustomization(resources: ["../base"], selfReference: selfReference)
            } else if (a.getName() == "examples/multibases/staging") {
                return new Kustomization(resources: ["../base"], selfReference: selfReference)
            } else if (a.getName() == "examples/multibases/production") {
                return new Kustomization(resources: ["../base"], selfReference: selfReference)
            } else if (a.getName() == "examples/multibases/base") {
                return new Kustomization(resources: ["pod.yaml"], selfReference: selfReference)
            }
        }
        filesToFetch.sort() == [
                referenceBase + "/examples/multibases/dev/kustomization.yaml",
                referenceBase + "/examples/multibases/staging/kustomization.yaml",
                referenceBase + "/examples/multibases/production/kustomization.yaml",
                referenceBase + "/examples/multibases/base/kustomization.yaml",
                referenceBase + "/examples/multibases/base/pod.yaml",
                referenceBase + "/examples/multibases/kustomization.yaml"
        ].sort()
    }

    def "isFolder checks if a string looks like a folder"() {
        given:
        def kustomizeProperties = Mock(RoscoKustomizeConfigurationProperties)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(Mock(KustomizationFileReader), Mock(ArtifactDownloader), kustomizeProperties)

        when:
        def isFolder = kustomizationTemplateUtils.isFolder(path)

        then:
        isFolder == result

        where:
        path              | result
        "../sibling"      | true
        "child"           | true
        "file.file"       | false
        "child/file.file" | false
    }

    def "buildBakeRecipe selects kustomize executable based on specified version"() {
        given:
        def referenceBase = "https://api.github.com/repos/org/repo/contents/base"

        def kustomizationFileReader = Mock(KustomizationFileReader)
        kustomizationFileReader.getKustomization(_, "kustomization.yml") >> {
            return new Kustomization(resources: ["deployment.yml", "service.yml"], selfReference: referenceBase + "/kustomization.yml")
        }

        def artifactDownloader = Mock(ArtifactDownloader)
        def kustomizeProperties = Mock(RoscoKustomizeConfigurationProperties)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, artifactDownloader, kustomizeProperties)
        def baseArtifact = Artifact.builder()
                .name("base/kustomization.yml")
                .reference(referenceBase + "/kustomization.yml")
                .artifactAccount("github")
                .type("github/file")
                .build()

        def request = new KustomizeBakeManifestRequest()
        request.inputArtifact = baseArtifact
        request.overrides = [:]

        when:
        request.templateRenderer = templateRenderer
        BakeRecipe recipe = BakeManifestEnvironment.create().withCloseable { env ->
            kustomizationTemplateUtils.buildBakeRecipe(env, request)
        }

        then:
        (0..1) * kustomizeProperties.v3ExecutablePath >> "kustomize"
        (0..1) * kustomizeProperties.v4ExecutablePath >> "kustomize4"
        recipe.command[0] == command

        where:
        templateRenderer                                | command
        BakeManifestRequest.TemplateRenderer.KUSTOMIZE  | "kustomize"
        BakeManifestRequest.TemplateRenderer.KUSTOMIZE4 | "kustomize4"
    }
}
