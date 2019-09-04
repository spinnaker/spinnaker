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
package com.netflix.spinnaker.keel.api.ec2.securityGroup

import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.model.Moniker
import de.danielbechler.diff.inclusion.Inclusion.EXCLUDED
import de.danielbechler.diff.introspection.ObjectDiffProperty

data class SecurityGroup(
  override val moniker: Moniker,
  val accountName: String,
  val region: String,
  val vpcName: String?,
  @get:ObjectDiffProperty(inclusion = EXCLUDED)
  val description: String?,
  val inboundRules: Set<SecurityGroupRule> = emptySet()
) : Monikered {
  override val id = "$accountName:$region:${moniker.name}"
}
