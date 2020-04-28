package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.netflix.spinnaker.keel.api.Moniker

@JsonTypeInfo(
  use = Id.NAME,
  include = As.PROPERTY,
  property = "loadBalancerType",
  defaultImpl = ClassicLoadBalancerModel::class // CloudDriver's response does not specify the loadBalancerType on classic load balancers
)
@JsonSubTypes(
  JsonSubTypes.Type(ClassicLoadBalancerModel::class, name = "classic"),
  JsonSubTypes.Type(ApplicationLoadBalancerModel::class, name = "application")
)
interface AmazonLoadBalancer {
  val moniker: Moniker?
  val loadBalancerName: String
  val loadBalancerType: String
  val availabilityZones: Set<String>
  val vpcId: String
  val subnets: Set<String>
  val scheme: String?
  val idleTimeout: Int
  val securityGroups: Set<String>
}
