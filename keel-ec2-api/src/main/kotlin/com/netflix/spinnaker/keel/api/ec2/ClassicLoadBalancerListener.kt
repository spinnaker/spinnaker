package com.netflix.spinnaker.keel.api.ec2

data class ClassicLoadBalancerListener(
  val internalProtocol: String,
  val internalPort: Int,
  val externalProtocol: String,
  val externalPort: Int,
  val sslCertificateId: String?
)
