/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.toSimpleLocations
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.ec2.jackson.DefaultSecurityGroupRuleDeserializer
import com.netflix.spinnaker.keel.ec2.jackson.IngressPortsDeserializer
import com.netflix.spinnaker.keel.ec2.jackson.SecurityGroupSpecDeserializer
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.deser.std.StdNodeBasedDeserializer
import tools.jackson.databind.jsontype.NamedType
import tools.jackson.databind.module.SimpleModule

class SecurityGroupSpecDeserializerTests {
  private val deserializers = SimpleModule().apply {
    addDeserializer(SecurityGroupSpec::class.java, SecurityGroupSpecDeserializer())
    addDeserializer(SecurityGroupRule::class.java, DefaultSecurityGroupRuleDeserializer())
    addDeserializer(IngressPorts::class.java, IngressPortsDeserializer())
    addDeserializer(ScopeSnapshot::class.java, ScopeSnapshotDeserializer())
  }
  private val subtypes = arrayOf(
    NamedType(SecurityGroupSpec::class.java, "ec2/security-group@v1"),
    NamedType(ClassicLoadBalancerSpec::class.java, "ec2/classic-load-balancer@v1")
  )
  private val mappers = listOf(
    configuredObjectMapper().registerKeelEc2ApiModule().rebuild()
      .addModule(deserializers).registerSubtypes(*subtypes).build(),
    configuredYamlMapper().registerKeelEc2ApiModule().rebuild()
      .addModule(deserializers).registerSubtypes(*subtypes).build()
  )
  private val environmentLocations = SubnetAwareLocations(
    account = "environment-account",
    subnet = "internal (environment-vpc)",
    vpc = "environment-vpc",
    regions = setOf(SubnetAwareRegionSpec("us-east-1", setOf("us-east-1a")))
  )
  private val rule = mapOf("protocol" to "TCP", "portRange" to "ALL")
  private val explicitGroup = mapOf(
    "moniker" to mapOf("app" to "fnord", "stack" to "override"),
    "locations" to mapOf(
      "account" to "resource-account",
      "vpc" to "resource-vpc",
      "regions" to listOf(mapOf("name" to "us-west-2"))
    ),
    "inboundRules" to listOf(rule + ("account" to "resource-account")),
    "overrides" to mapOf("us-west-2" to mapOf("inboundRules" to listOf(rule + ("account" to "resource-account"))))
  )

  @Test
  fun `resource overrides do not leak into subsequent security groups or subnet aware resources`() {
    val inheritedGroup = mapOf(
      "moniker" to mapOf("app" to "fnord", "stack" to "inherited"),
      "inboundRules" to listOf(rule + ("account" to "environment-account"))
    )
    val loadBalancer = mapOf(
      "moniker" to mapOf("app" to "fnord", "stack" to "lb"),
      "healthCheck" to mapOf("target" to "TCP:8080")
    )
    val input = mapOf(
      "name" to "test",
      "locations" to environmentLocations,
      "resources" to listOf(
        mapOf("kind" to "ec2/security-group@v1", "spec" to explicitGroup),
        mapOf("kind" to "ec2/security-group@v1", "spec" to inheritedGroup),
        mapOf("kind" to "ec2/classic-load-balancer@v1", "spec" to loadBalancer)
      )
    )

    mappers.forEach { mapper ->
      val environment = mapper.readValue(mapper.writeValueAsString(input), SubmittedEnvironment::class.java)
      val groups = environment.resources.map { it.spec }.filterIsInstance<SecurityGroupSpec>()
      val overridden = groups.single { it.moniker.stack == "override" }
      val inherited = groups.single { it.moniker.stack == "inherited" }
      val subnetAware = environment.resources.map { it.spec }.filterIsInstance<ClassicLoadBalancerSpec>().single()

      assertEquals("resource-account", overridden.locations.account)
      assertEquals("resource-vpc", overridden.locations.vpc)
      assertEquals("fnord-override", (overridden.inboundRules.single() as ReferenceRule).name)
      val overrideRule = overridden.overrides.getValue("us-west-2").inboundRules!!.single() as ReferenceRule
      assertEquals("fnord-override", overrideRule.name)
      assertEquals(environmentLocations.toSimpleLocations(), inherited.locations)
      assertEquals("fnord-inherited", (inherited.inboundRules.single() as ReferenceRule).name)
      assertEquals(environmentLocations, subnetAware.locations)
    }
  }

  @Test
  fun `successful deserialization restores parent attributes`() {
    mappers.forEach { mapper ->
      val snapshot = mapper.readerFor(ScopeSnapshot::class.java)
        .withAttribute("name", "parent-name")
        .withAttribute("locations", environmentLocations)
        .readValue<ScopeSnapshot>(mapper.writeValueAsString(explicitGroup))

      assertFalse(snapshot.failed)
      assertNotNull(snapshot.group)
      assertEquals("parent-name", snapshot.name)
      assertSame(environmentLocations, snapshot.locations)
    }
  }

  @Test
  fun `malformed inbound rules and overrides restore parent attributes`() {
    val malformed = listOf(
      explicitGroup + ("inboundRules" to listOf(mapOf("protocol" to "INVALID", "portRange" to "ALL"))),
      explicitGroup + ("overrides" to mapOf(
        "us-west-2" to mapOf("inboundRules" to listOf(mapOf("protocol" to "INVALID")))
      ))
    )

    mappers.forEach { mapper ->
      malformed.forEach { input ->
        val snapshot = mapper.readerFor(ScopeSnapshot::class.java)
          .withAttribute("name", "parent-name")
          .withAttribute("locations", environmentLocations)
          .readValue<ScopeSnapshot>(mapper.writeValueAsString(input))

        assertTrue(snapshot.failed)
        assertEquals("parent-name", snapshot.name)
        assertSame(environmentLocations, snapshot.locations)
      }
    }
  }

  @Test
  fun `absent parent attributes remain absent after success or failure`() {
    val malformed = explicitGroup + ("inboundRules" to listOf(mapOf("protocol" to "INVALID")))
    mappers.forEach { mapper ->
      listOf(explicitGroup, malformed).forEach { input ->
        val snapshot = mapper.readValue(mapper.writeValueAsString(input), ScopeSnapshot::class.java)

        assertEquals(input == malformed, snapshot.failed)
        assertEquals(null, snapshot.name)
        assertEquals(null, snapshot.locations)
      }
    }
  }

  private data class ScopeSnapshot(
    val group: SecurityGroupSpec?,
    val failed: Boolean,
    val name: Any?,
    val locations: Any?
  )

  private class ScopeSnapshotDeserializer : StdNodeBasedDeserializer<ScopeSnapshot>(ScopeSnapshot::class.java) {
    override fun convert(root: JsonNode, context: DeserializationContext): ScopeSnapshot {
      val result = runCatching { context.readTreeAsValue(root, SecurityGroupSpec::class.java) }
      return ScopeSnapshot(
        result.getOrNull(),
        result.isFailure,
        context.getAttribute("name"),
        context.getAttribute("locations")
      )
    }
  }
}
