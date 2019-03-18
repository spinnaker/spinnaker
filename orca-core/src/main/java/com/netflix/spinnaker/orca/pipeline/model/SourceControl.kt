package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class SourceControl
@JsonCreator constructor(
        @param:JsonProperty("name") val name: String,
        @param:JsonProperty("branch") val branch: String,
        @param:JsonProperty("sha1") val sha1: String
)
