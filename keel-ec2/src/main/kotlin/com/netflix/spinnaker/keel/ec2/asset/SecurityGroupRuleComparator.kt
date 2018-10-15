/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.ec2.asset

import com.google.common.collect.ComparisonChain
import com.google.common.collect.Ordering
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.ec2.CidrRule
import com.netflix.spinnaker.keel.ec2.CrossRegionReferenceRule
import com.netflix.spinnaker.keel.ec2.PortRange
import com.netflix.spinnaker.keel.ec2.ReferenceRule
import com.netflix.spinnaker.keel.ec2.SecurityGroup
import com.netflix.spinnaker.keel.ec2.SecurityGroupRuleOrBuilder
import com.netflix.spinnaker.keel.ec2.SelfReferencingRule
import com.netflix.spinnaker.keel.proto.isA
import com.netflix.spinnaker.keel.proto.pack
import com.netflix.spinnaker.keel.proto.unpack

object SecurityGroupRuleComparator : Comparator<SecurityGroupRuleOrBuilder> {
  override fun compare(a: SecurityGroupRuleOrBuilder?, b: SecurityGroupRuleOrBuilder?): Int =
    ComparisonChain
      .start()
      .compare(a?.referenceRule, b?.referenceRule, nullsFirst(Ordering.from(ReferenceRuleComparator)))
      .compare(a?.crossRegionReferenceRule, b?.crossRegionReferenceRule, nullsFirst(Ordering.from(CrossRegionReferenceRuleComparator)))
      .compare(a?.selfReferencingRule, b?.selfReferencingRule, nullsFirst(Ordering.from(SelfReferencingRuleComparator)))
      .compare(a?.cidrRule, b?.cidrRule, nullsFirst(Ordering.from(CidrRuleComparator)))
      .result()
}

object ReferenceRuleComparator : Comparator<ReferenceRule> {
  override fun compare(a: ReferenceRule?, b: ReferenceRule?): Int =
    ComparisonChain
      .start()
      .compare(a?.protocol, b?.protocol, nullsFirst())
      .compare(a?.name, b?.name, nullsFirst())
      .compare(a?.portRangeList, b?.portRangeList, nullsFirst(Ordering.from<PortRange>(PortRangeComparator).lexicographical<PortRange>()))
      .result()
}

object CrossRegionReferenceRuleComparator : Comparator<CrossRegionReferenceRule> {
  override fun compare(a: CrossRegionReferenceRule?, b: CrossRegionReferenceRule?): Int =
    ComparisonChain
      .start()
      .compare(a?.protocol, b?.protocol, nullsFirst())
      .compare(a?.account, b?.account, nullsFirst())
      .compare(a?.vpcName, b?.vpcName, nullsFirst())
      .compare(a?.name, b?.name, nullsFirst())
      .compare(a?.portRangeList, b?.portRangeList, nullsFirst(Ordering.from<PortRange>(PortRangeComparator).lexicographical<PortRange>()))
      .result()
}

object SelfReferencingRuleComparator : Comparator<SelfReferencingRule> {
  override fun compare(a: SelfReferencingRule?, b: SelfReferencingRule?): Int =
    ComparisonChain
      .start()
      .compare(a?.protocol, b?.protocol, nullsFirst())
      .compare(a?.portRangeList, b?.portRangeList, nullsFirst(Ordering.from<PortRange>(PortRangeComparator).lexicographical<PortRange>()))
      .result()
}

object CidrRuleComparator : Comparator<CidrRule> {
  override fun compare(a: CidrRule?, b: CidrRule?): Int =
    ComparisonChain
      .start()
      .compare(a?.protocol, b?.protocol, nullsFirst())
      .compare(a?.blockRange, b?.blockRange, nullsFirst())
      .compare(a?.portRangeList, b?.portRangeList, nullsFirst(Ordering.from<PortRange>(PortRangeComparator).lexicographical<PortRange>()))
      .result()
}

object PortRangeComparator : Comparator<PortRange> {
  override fun compare(a: PortRange?, b: PortRange?): Int =
    ComparisonChain
      .start()
      .compare(a?.startPort, b?.startPort, nullsFirst())
      .compare(a?.endPort, b?.endPort, nullsFirst())
      .result()
}

/**
 * Makes sure all repeating fields are ordered such that asset specs can be
 * compared without being unpacked.
 */
fun Asset.canonicalize(): Asset {
  return when {
    spec.isA<SecurityGroup>() -> {
      val securityGroupSpec = spec.unpack<SecurityGroup>()
      toBuilder()
        .apply {
          spec = securityGroupSpec.canonicalize().pack()
        }
        .build()
    }
    else -> throw IllegalStateException("${spec.typeUrl} is an unsupported asset type")
  }
}

private fun SecurityGroup.canonicalize(): SecurityGroup {
  val originalInboundRuleList = inboundRuleList
  return SecurityGroup
    .newBuilder(this).apply {
      clearInboundRule()
      originalInboundRuleList.sortedWith(SecurityGroupRuleComparator).forEach { originalRule ->
        addInboundRule(originalRule.toBuilder().apply {
          when {
            hasReferenceRule() ->
              referenceRule = referenceRule.canonicalize()
            hasCrossRegionReferenceRule() ->
              crossRegionReferenceRule = crossRegionReferenceRule.canonicalize()
            hasSelfReferencingRule() ->
              selfReferencingRule = selfReferencingRule.canonicalize()
            hasCidrRule() ->
              cidrRule = cidrRule.canonicalize()
          }
        }.build())
      }
    }
    .build()
}

private fun ReferenceRule.canonicalize(): ReferenceRule {
  val originalPortRangeList = portRangeList
  return toBuilder()
    .apply {
      clearPortRange()
      originalPortRangeList.sortedWith(PortRangeComparator).forEach {
        addPortRange(it)
      }
    }
    .build()
}

private fun CrossRegionReferenceRule.canonicalize(): CrossRegionReferenceRule {
  val originalPortRangeList = portRangeList
  return toBuilder()
    .apply {
      clearPortRange()
      originalPortRangeList.sortedWith(PortRangeComparator).forEach {
        addPortRange(it)
      }
    }
    .build()
}

private fun SelfReferencingRule.canonicalize(): SelfReferencingRule {
  val originalPortRangeList = portRangeList
  return toBuilder()
    .apply {
      clearPortRange()
      originalPortRangeList.sortedWith(PortRangeComparator).forEach {
        addPortRange(it)
      }
    }
    .build()
}

private fun CidrRule.canonicalize(): CidrRule {
  val originalPortRangeList = portRangeList
  return toBuilder()
    .apply {
      clearPortRange()
      originalPortRangeList.sortedWith(PortRangeComparator).forEach {
        addPortRange(it)
      }
    }
    .build()
}
