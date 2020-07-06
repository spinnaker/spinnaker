package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.netflix.spinnaker.keel.jackson.ResourceKindDeserializer

@JsonSerialize(using = ToStringSerializer::class)
@JsonDeserialize(using = ResourceKindDeserializer::class)
interface ResourceKindMixin
