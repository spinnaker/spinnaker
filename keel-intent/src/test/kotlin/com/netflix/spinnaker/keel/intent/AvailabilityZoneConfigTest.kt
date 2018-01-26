package com.netflix.spinnaker.keel.intent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.config.KeelConfiguration
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.intent.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.intent.AvailabilityZoneConfig.Manual
import org.junit.jupiter.api.Test

object AvailabilityZoneConfigTest {

  val mapper = KeelConfiguration()
    .apply { properties = KeelProperties() }
    .objectMapper(ObjectMapper(), listOf())

  private val automaticJson = """{"zones":"automatic"}"""
  private val automaticZones = Container(zones = Automatic)
  private val manualJson = """{"zones":["us-west-2a","us-west-2b","us-west-2c"]}"""
  private val manualZones = Container(zones = Manual(setOf("us-west-2a", "us-west-2b", "us-west-2c")))

  @Test
  fun `can parse automatic AZ config`() {
    mapper.readValue<Container>(automaticJson).apply {
      zones shouldEqual Automatic
    }
  }

  @Test
  fun `can serialize automatic AZ config`() {
    mapper.writeValueAsString(automaticZones) shouldEqual automaticJson
  }

  @Test
  fun `can parse manual AZ config`() {
    mapper.readValue<Container>(manualJson).apply {
      zones shouldEqual manualZones.zones
    }
  }

  @Test
  fun `can serialize manual AZ config`() {
    mapper.writeValueAsString(manualZones) shouldEqual manualJson
  }

  private data class Container(
    val zones: AvailabilityZoneConfig
  )
}
