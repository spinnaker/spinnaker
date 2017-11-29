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
package com.netflix.spinnaker.keel.policy

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.netflix.spinnaker.keel.attribute.Attribute
import kotlin.reflect.KClass

/**
 * A Policy defines organizational- & team-level standards that all (matching) Intents must adhere to. If an Intent
 * does not meet the criteria defined by a Policy, it will not be scheduled for convergence. Even ad-hoc Intents that
 * will not be scheduled in the future will be submitted for compliance to Policies.
 *
 * Policies can also be used to define additional behavior, such as restricting when an Intent can be applied (e.g.
 * via execution windows). In these cases, it will be desired to allow an Intent to self-define the acceptable windows
 * to execute in. Policies can define a list of required Intent Attributes that must be set before the Policy will be
 * enacted.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class Policy<out S : PolicySpec>(
  val kind: String,
  val spec: S
) {
  val id: String = this.javaClass.simpleName
  val requiredAttributes: List<KClass<Attribute<*>>> = listOf()
}

interface PolicySpec
