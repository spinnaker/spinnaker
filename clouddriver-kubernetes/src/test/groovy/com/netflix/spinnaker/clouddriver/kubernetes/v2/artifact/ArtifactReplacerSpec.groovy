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
    image                         || name
    "nginx:112"                   || "nginx"
    "nginx:1.12-alpine"           || "nginx"
    "my-nginx:100000"             || "my-nginx"
    "my.nginx:100000"             || "my.nginx"
    "reg/repo:1.2.3"              || "reg/repo"
    "reg.default.svc/r/j:485fabc" || "reg.default.svc/r/j"
    "reg:5000/r/j:485fabc"        || "reg:5000/r/j"
    "reg:5000/r__j:485fabc"       || "reg:5000/r__j"
    "clouddriver"                 || "clouddriver"
    "localhost:5000/test/busybox@sha256:cbbf2f9a99b47fc460d422812b6a5adff7dfee951d8fa2e4a98caa0382cfbdbf" \
      || "localhost:5000/test/busybox"
  }

  @Unroll
  def "does not generate invalid Docker artifacts from image names"() {
    expect:
    def deploymentManifest = """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app-deployment
  labels:
    app: my-app
spec:
  template:
    spec:
      containers:
      - name: container
        image: $image
      - name: other-container
        image: my-image:1.0
"""
    def artifactReplacer = new ArtifactReplacer()
    artifactReplacer.addReplacer(ArtifactReplacerFactory.dockerImageReplacer())
    def manifest = stringToManifest(deploymentManifest)
    def artifacts = artifactReplacer.findAll(manifest)

    artifacts.size() == 1
    Artifact artifact = artifacts.toList().get(0)
    artifact.getType() == ArtifactTypes.DOCKER_IMAGE.toString()
    artifact.getName() == "my-image"
    artifact.getReference() == "my-image:1.0"

    where:
    image                                            | _
    ":500"                                           | _
    "'@registry.default.svc/myrepo/jenkins:485fabc'" | _
    "registry___myrepo/"                             | _
    "'!!clouddriver'"                                | _
    "localhost@5000/test/busybox@sha256:cbbf2f9"     | _
    "registry/myrepo/_myimage:1.2.3"                 | _
    "nginx:-alpine"                                  | _
    "nginx:.alpine"                                  | _
    "reg:5000/r___j:485fabc"                         | _
    "my..nginx:100000"                               | _
    "my-_-nginx:100000"                              | _
  }
}
