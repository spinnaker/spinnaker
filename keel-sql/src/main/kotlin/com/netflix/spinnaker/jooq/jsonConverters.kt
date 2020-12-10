package com.netflix.spinnaker.jooq

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.jooq.JSON
import org.jooq.impl.AbstractConverter

abstract class JsonConverter<U : Any>(toType: Class<U>) : AbstractConverter<JSON, U>(
  JSON::class.java,
  toType
) {
  private val mapper = configuredObjectMapper()

  override fun from(databaseObject: JSON?): U? =
    databaseObject?.let { mapper.readValue(it.data(), toType()) }

  override fun to(userObject: U?): JSON? =
    userObject?.let { JSON.valueOf(mapper.writeValueAsString(it)) }
}

class Metadata(delegate: Map<String, Any>) : Map<String, Any?> by delegate

@Suppress("UNCHECKED_CAST")
class JsonToMapConverter : JsonConverter<Map<String, Any?>>(Map::class.java as Class<Map<String, Any?>>)
class GitMetadataConverter : JsonConverter<GitMetadata>(GitMetadata::class.java)
class BuildMetadataConverter : JsonConverter<BuildMetadata>(BuildMetadata::class.java)
class LifecycleEventConverter : JsonConverter<LifecycleEvent>(LifecycleEvent::class.java)
class PersistentEventConverter : JsonConverter<PersistentEvent>(PersistentEvent::class.java)
