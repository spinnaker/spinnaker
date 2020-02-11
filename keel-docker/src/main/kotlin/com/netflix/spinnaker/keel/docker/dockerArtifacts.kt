package com.netflix.spinnaker.keel.docker

import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact

val DockerArtifact.organization
  get() = name.split("/").first()

val DockerArtifact.image
  get() = name.split("/").last()
