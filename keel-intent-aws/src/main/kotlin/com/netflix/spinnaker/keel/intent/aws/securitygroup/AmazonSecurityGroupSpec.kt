/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intent.aws.securitygroup

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.keel.intent.securitygroup.NamedReferenceSupport
import com.netflix.spinnaker.keel.intent.securitygroup.PortRangeSupport
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupPortRange
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupRule
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupSpec
import java.util.*

@JsonTypeName("aws")
data class AmazonSecurityGroupSpec(
  override val application: String,
  override val name: String,
  override val cloudProvider: String,
  override val accountName: String,
  override val region: String,
  override val inboundRules: Set<SecurityGroupRule>,
  val outboundRules: Set<SecurityGroupRule>,
  // We don't care to support EC2 Classic, but for some reason clouddriver returns nulls (and isn't "default" vpcs)
  val vpcName: String?,
  val description: String
) : SecurityGroupSpec()

@JsonTypeName("crossAccountRef")
data class CrossAccountReferenceSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String,
  override val name: String,
  val account: String,
  val vpcName: String
) : SecurityGroupRule(), PortRangeSupport, NamedReferenceSupport
