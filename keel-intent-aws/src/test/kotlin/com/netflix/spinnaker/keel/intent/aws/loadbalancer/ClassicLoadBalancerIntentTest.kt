package com.netflix.spinnaker.keel.intent.aws.loadbalancer

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.config.configureObjectMapper
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.intent.LoadBalancerIntent
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.AvailabilityZoneConfig.Manual
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.HealthEndpoint.Http
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.Protocol.SSL
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.Protocol.TCP
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.Scheme.internal
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.ClassSubtypeLocator
import org.junit.jupiter.api.Test

object ClassicLoadBalancerIntentTest {

  val mapper = configureObjectMapper(
    ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL),
    KeelProperties(),
    listOf(
      ClassSubtypeLocator(ClassicLoadBalancerSpec::class.java, listOf("com.netflix.spinnaker.keel.intent"))
    )
  )

  @Test
  fun `can serialize to expected JSON format`() {
    val serialized = mapper.convertValue<Map<String, Any>>(elb)
    val deserialized = mapper.readValue<Map<String, Any>>(json)

    serialized shouldMatch equalTo(deserialized)
  }

  @Test
  fun `can deserialize from expected JSON format`() {
    mapper.readValue<LoadBalancerIntent>(json).apply {
      spec shouldEqual elb.spec
    }
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun `availability zones defaults to automatic`() {
    mapper.readValue<Map<String, Any>>(json)
      .apply { (get("spec") as MutableMap<String, Any>).remove("availabilityZones") }
      .let {
        mapper.convertValue<LoadBalancerIntent>(it).apply {
          (spec as ClassicLoadBalancerSpec).availabilityZones shouldEqual Automatic
        }
      }
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun `availability zones can be configured manually`() {
    mapper.readValue<Map<String, Any>>(json)
      .apply { (get("spec") as MutableMap<String, Any>)["availabilityZones"] = listOf("us-west-2a", "us-west-2c") }
      .let {
        mapper.convertValue<LoadBalancerIntent>(it).apply {
          (spec as ClassicLoadBalancerSpec).availabilityZones shouldEqual Manual(setOf("us-west-2a", "us-west-2c"))
        }
      }
  }

  val elb = LoadBalancerIntent(
    ClassicLoadBalancerSpec(
      application = "covfefe",
      name = "covfefe-elb",
      accountName = "mgmt",
      region = "us-west-2",
      securityGroupNames = sortedSetOf("covfefe", "nf-infrastructure", "nf-datacenter"),
      availabilityZones = Automatic,
      scheme = internal,
      listeners = setOf(ClassicListener(TCP, 80, TCP, 7001), ClassicListener(SSL, 443, SSL, 7002, "my-ssl-certificate")),
      healthCheck = HealthCheckSpec(Http(7001, "/healthcheck")),
      vpcName = "vpcName",
      subnets = "internal"
    )
  )

  val json = """
{
  "kind": "LoadBalancer",
  "spec": {
    "kind": "aws.ClassicLoadBalancer",
    "application": "covfefe",
    "name": "covfefe-elb",
    "accountName": "mgmt",
    "region": "us-west-2",
    "securityGroupNames": [
      "covfefe",
      "nf-datacenter",
      "nf-infrastructure"
    ],
    "vpcName": "vpcName",
    "subnets": "internal",
    "availabilityZones": "automatic",
    "scheme": "internal",
    "listeners": [
      {
        "protocol": "TCP",
        "loadBalancerPort": 80,
        "instanceProtocol": "TCP",
        "instancePort": 7001
      },
      {
        "protocol": "SSL",
        "loadBalancerPort": 443,
        "instanceProtocol": "SSL",
        "instancePort": 7002,
        "sslCertificateId": "my-ssl-certificate"
      }
    ],
    "healthCheck": {
      "target": {
        "port": 7001,
        "path": "/healthcheck",
        "protocol": "HTTP"
      },
      "interval": 10,
      "timeout": 5,
      "unhealthyThreshold": 2,
      "healthyThreshold": 10
    }
  },
  "status": "ACTIVE",
  "labels": {},
  "attributes": [],
  "id": "LoadBalancer:aws:mgmt:us-west-2:covfefe-elb",
  "schema": "0"
}
"""
}
