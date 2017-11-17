/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intent

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareIntentSpec
import com.netflix.spinnaker.keel.Intent
import java.util.*

private const val KIND = "SecurityGroup"
private const val CURRENT_SCHEMA = "1"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class SecurityGroupIntent
@JsonCreator constructor(spec: SecurityGroupSpec) : Intent<SecurityGroupSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  // TODO rz - Should region be included in the mix? If someone removes a region, the intent ID would change, but what
  // if someone wants to have two different intents managing the same named security group in different regions (rules
  // are different region-to-region). Is this actually something to be concerned about? Be hard to change the ID strat
  // after the fact...
  override val id = "${KIND}:${spec.cloudProvider}:${spec.accountName}:${spec.name}"
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class SecurityGroupSpec : ApplicationAwareIntentSpec() {
  abstract val name: String
  abstract val cloudProvider: String
  abstract val accountName: String
  abstract val regions: Set<String>
  abstract val inboundRules: Set<SecurityGroupRule>
}

@JsonTypeName("aws")
data class AmazonSecurityGroupSpec(
  override val application: String,
  override val name: String,
  override val cloudProvider: String,
  override val accountName: String,
  override val regions: Set<String>,
  override val inboundRules: Set<SecurityGroupRule>,
  val outboundRules: Set<SecurityGroupRule>,
  val vpcName: String?,
  val description: String
) : SecurityGroupSpec()

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class SecurityGroupRule

data class SecurityGroupPortRange(
  val startPort: Int,
  val endPort: Int
) : Comparable<SecurityGroupPortRange> {

  override fun compareTo(other: SecurityGroupPortRange): Int {
    return when {
        startPort < other.startPort -> -1
        startPort > other.startPort -> 1
        else -> {
          if (endPort < other.startPort) {
            return -1
          } else if (endPort > other.endPort) {
            return 1
          }
          0
        }
    }
  }
}

// TODO rz - should probably be named differently
interface NamedReferenceSupport {
  val name: String
}

interface PortRangeSupport : NamedReferenceSupport {
  val portRanges: SortedSet<SecurityGroupPortRange>
  val protocol: String
}

@JsonTypeName("ref")
data class ReferenceSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String,
  override val name: String
) : SecurityGroupRule(), PortRangeSupport

@JsonTypeName("crossAccountRef")
data class CrossAccountReferenceSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String,
  override val name: String,
  val account: String,
  val region: String,
  val vpcName: String
) : SecurityGroupRule(), PortRangeSupport

@JsonTypeName("http")
data class HttpSecurityGroupRule(
  val paths: List<String>,
  val host: String
) : SecurityGroupRule()

@JsonTypeName("cidr")
data class CidrSecurityGroupRule(
  val protocol: String,
  val blockRange: String
) : SecurityGroupRule()
