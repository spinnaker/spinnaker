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
package com.netflix.spinnaker.keel

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * A Policy can be attached to a single Intent, or applied globally via Keel configuration / dynamic admin API. These
 * classes are used to define additional behavior of how an Intent should behave under specific conditions, whether
 * an Intent should be applied given the condition of the managed system and/or Spinnaker state, and so-on.
 *
 * Matchers are primarily attached to policies only on Keel configuration / admin API usage, allowing a policy to be
 * applied globally, matching a subset of Intents. For example, an EnabledPolicy could be set with a falsey value, and
 * use Matchers to narrow by the PriorityMatcher so that only CRITICAL Priority Intents are enabled, across all
 * applications.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class Policy {
  fun getId(): String = this.javaClass.simpleName
}
