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
package com.netflix.spinnaker.keel.intent.securitygroup

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareIntentSpec
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.intent.SCHEMA_PROPERTY
import java.util.*

private const val KIND = "SecurityGroup"
private const val CURRENT_SCHEMA = "0"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class SecurityGroupIntent
@JsonCreator constructor(spec: SecurityGroupSpec) : Intent<SecurityGroupSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  @JsonIgnore override val defaultId = "$KIND:${spec.cloudProvider}:${spec.accountName}:${spec.region}:${spec.name}"
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class SecurityGroupSpec : ApplicationAwareIntentSpec {
  abstract val name: String
  abstract val cloudProvider: String
  abstract val accountName: String
  abstract val region: String
  abstract val inboundRules: Set<SecurityGroupRule>
}

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

interface NamedReferenceSupport {
  val name: String
}

interface PortRangeSupport {
  val portRanges: SortedSet<SecurityGroupPortRange>
  val protocol: String
}

@JsonTypeName("ref")
data class ReferenceSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String,
  override val name: String
) : SecurityGroupRule(), NamedReferenceSupport, PortRangeSupport

@JsonTypeName("self")
data class SelfReferencingSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String
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
