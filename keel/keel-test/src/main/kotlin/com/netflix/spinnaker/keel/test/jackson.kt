package com.netflix.spinnaker.keel.test

import tools.jackson.databind.jsontype.NamedType
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper

fun configuredTestObjectMapper(): JsonMapper = configuredObjectMapper()
  .rebuild()
  .registerArtifactSubtypes()
  .build()

fun configuredTestYamlMapper(): YAMLMapper = configuredYamlMapper()
  .rebuild()
  .registerArtifactSubtypes()
  .build()

private fun JsonMapper.Builder.registerArtifactSubtypes() =
  this.apply {
    registerSubtypes(
      NamedType(DebianArtifact::class.java, "deb"),
      NamedType(DockerArtifact::class.java, "docker")
    )
  }

private fun YAMLMapper.Builder.registerArtifactSubtypes() =
  this.apply {
    registerSubtypes(
      NamedType(DebianArtifact::class.java, "deb"),
      NamedType(DockerArtifact::class.java, "docker")
    )
  }
