package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class BuildInfo<A>
@JsonCreator constructor(
        @param:JsonProperty("name") val name: String,
        @param:JsonProperty("number") val number: Int,
        @param:JsonProperty("url") val url: String,
        @JsonProperty("artifacts") val artifacts: List<A>? = emptyList(),
        @JsonProperty("scm") val scm: List<SourceControl>? = emptyList(),
        @param:JsonProperty("building") val isBuilding: Boolean,
        @param:JsonProperty("result") val result: String?,
        @param:JsonProperty("fullDisplayName") val fullDisplayName: String = "$name#$number"
)
