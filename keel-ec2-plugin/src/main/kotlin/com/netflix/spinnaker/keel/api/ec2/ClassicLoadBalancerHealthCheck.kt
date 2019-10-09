package com.netflix.spinnaker.keel.api.ec2

import java.time.Duration

data class ClassicLoadBalancerHealthCheck(
  val target: String, // "$healthCheckProtocol:$healthCheckPort$healthCheckPath",
  val interval: Duration = Duration.ofSeconds(10),
  val healthyThreshold: Int = 5,
  val unhealthyThreshold: Int = 2,
  val timeout: Duration = Duration.ofSeconds(5)
)
