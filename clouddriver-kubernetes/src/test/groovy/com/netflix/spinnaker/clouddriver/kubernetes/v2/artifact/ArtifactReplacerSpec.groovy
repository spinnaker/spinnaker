package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Unroll


class ArtifactReplacerSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml()

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  @Unroll
  def "correctly extracts Docker artifacts from image names"() {
    expect:
    def deploymentManifest = """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app-deployment
  labels:
    app: my-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
      - name: container
        image: $image
        ports:
        - containerPort: 80
"""
    def artifactReplacer = new ArtifactReplacer()
    artifactReplacer.addReplacer(ArtifactReplacerFactory.dockerImageReplacer())
    def manifest = stringToManifest(deploymentManifest)
    def artifacts = artifactReplacer.findAll(manifest)

    artifacts.size() == 1
    Artifact artifact = artifacts.toList().get(0)
    artifact.getType() == ArtifactTypes.DOCKER_IMAGE.toString()
    artifact.getName() == name
    artifact.getReference() == image

    where:
    image                                       || name
    "nginx:112"                                 || "nginx"
    "nginx:1.12-alpine"                         || "nginx"
    "my-nginx:100000"                           || "my-nginx"
    "my.nginx:100000"                           || "my.nginx"
    "reg/repo:1.2.3"                            || "reg/repo"
    "reg.repo:123@sha256:13"                    || "reg.repo:123"
    "reg.default.svc/r/j:485fabc"               || "reg.default.svc/r/j"
    "reg:5000/r/j:485fabc"                      || "reg:5000/r/j"
    "reg:5000/r__j:485fabc"                     || "reg:5000/r__j"
    "clouddriver"                               || "clouddriver"
    "clouddriver@sha256:9145"                   || "clouddriver"
    "localhost:5000/test/busybox@sha256:cbbf22" || "localhost:5000/test/busybox"
  }
}
