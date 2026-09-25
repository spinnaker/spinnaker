package com.netflix.spinnaker.keel.jackson.mixins

import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.ser.std.ToStringSerializer
import com.netflix.spinnaker.keel.jackson.ResourceKindDeserializer

@JsonSerialize(using = ToStringSerializer::class)
@JsonDeserialize(using = ResourceKindDeserializer::class)
interface ResourceKindMixin
