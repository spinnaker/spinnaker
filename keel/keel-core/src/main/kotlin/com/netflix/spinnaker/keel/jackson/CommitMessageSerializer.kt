package com.netflix.spinnaker.keel.jackson

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer

internal object CommitMessageSerializer : StdSerializer<String>(String::class.java) {
  private val SLACK_LINK_REGEX = Regex("<(http.+)\\|(.+)>")

  override fun serialize(value: String, gen: JsonGenerator, provider: SerializationContext) {
    gen.writeString(
      SLACK_LINK_REGEX.replace(value) { match ->
       "[${match.groups[2]?.value}](${match.groups[1]?.value})"
      }
    )
  }
}
