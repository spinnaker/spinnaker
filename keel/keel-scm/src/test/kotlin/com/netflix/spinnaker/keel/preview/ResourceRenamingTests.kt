package com.netflix.spinnaker.keel.preview

import com.netflix.spinnaker.keel.api.ec2.AllPorts
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.TargetGroup
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.UDP
import com.netflix.spinnaker.keel.test.applicationLoadBalancer
import com.netflix.spinnaker.keel.test.classicLoadBalancer
import com.netflix.spinnaker.keel.test.ec2Cluster
import com.netflix.spinnaker.keel.test.randomString
import com.netflix.spinnaker.keel.test.securityGroup
import com.netflix.spinnaker.keel.test.titusCluster
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.endsWith
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThanOrEqualTo

internal class ResourceRenamingTests {
  private val securityGroup = securityGroup().run {
    copy(
      spec = spec.copy(
        inboundRules = setOf(
          ReferenceRule(protocol = TCP, name = name, portRange = AllPorts),
          ReferenceRule(protocol = UDP, name = name, portRange = AllPorts)
        )
      )
    )
  }

  private val applicationLoadBalancer = applicationLoadBalancer().run {
    copy(
      spec = spec.copy(
        targetGroups = setOf(
          TargetGroup(name = "tg1", port = 80),
          TargetGroup(name = "tg2", port = 443)
        )
      )
    )
  }

  private val classicLoadBalancer = classicLoadBalancer()
  private val titusCluster = titusCluster()
  private val ec2Cluster = ec2Cluster()

  @Test
  fun `deep renaming a security group renames the security group and self-referencing ingress rules`() {
    expectThat(securityGroup.deepRename("suffix"))
      .run {
        get { name }.endsWith("-suffix")
        get { id }.endsWith("-suffix")
        get { metadata["id"] as String }.endsWith("-suffix")
        get { spec.inboundRules.filterIsInstance<ReferenceRule>() }
          .all { get { name }.endsWith("-suffix") }
      }
  }

  @Test
  fun `deep renaming an ALB renames the ALB and target groups`() {
    expectThat(applicationLoadBalancer.deepRename("suffix"))
      .run {
        get { name }.endsWith("-suffix")
        get { id }.endsWith("-suffix")
        get { metadata["id"] as String }.endsWith("-suffix")
        get { spec.targetGroups }
          .all { get { name }.endsWith("-suffix") }
      }
  }

  @Test
  fun `deep renaming an ALB respects max name length`() {
    val albWithLongName = applicationLoadBalancer.run {
      copy(
        spec = spec.copy(
          moniker = spec.moniker.copy(
            detail = randomString(32 - spec.moniker.toName().length - 1)
          )
        )
      )
    }
    expectThat(albWithLongName.name.length).isEqualTo(32) // max length
    expectThat(albWithLongName.deepRename("suffix"))
      .run {
        // still includes the suffix, but does not go over max length
        get { name }.endsWith("-suffix")
        get { name.length }.isLessThanOrEqualTo(32)
      }
  }

  @Test
  fun `deep renaming a CLB renames the CLB`() {
    expectThat(classicLoadBalancer.deepRename("suffix"))
      .run {
        get { name }.endsWith("-suffix")
        get { id }.endsWith("-suffix")
        get { metadata["id"] as String }.endsWith("-suffix")
      }
  }

  @Test
  fun `deep renaming a Titus cluster renames the cluster`() {
    expectThat(titusCluster.deepRename("suffix"))
      .run {
        get { name }.endsWith("-suffix")
        get { id }.endsWith("-suffix")
        get { metadata["id"] as String }.endsWith("-suffix")
      }
  }

  @Test
  fun `deep renaming an EC2 cluster renames the cluster`() {
    expectThat(ec2Cluster.deepRename("suffix"))
      .run {
        get { name }.endsWith("-suffix")
        get { id }.endsWith("-suffix")
        get { metadata["id"] as String }.endsWith("-suffix")
      }
  }
}
