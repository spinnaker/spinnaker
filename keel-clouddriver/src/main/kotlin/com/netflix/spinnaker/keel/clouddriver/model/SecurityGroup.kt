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
package com.netflix.spinnaker.keel.clouddriver.model

data class SecurityGroup(
  val type: String,
  val id: String?,
  val name: String,
  val description: String?,
  val accountName: String,
  val region: String,
  val vpcId: String?,
  // TODO rz - This isn't fully representative of the rules that are allowed.
  val inboundRules: List<SecurityGroupIngress>
)

data class SecurityGroupIngress(
  val name: String,
  val startPort: Int,
  val endPort: Int,
  val type: String,
  val securityGroup: SecurityGroup?
)
