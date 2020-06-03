/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.keel.api.constraints

/**
 * TODO: Docs.
 */
enum class ConstraintStatus(private val passed: Boolean, private val failed: Boolean) {
  NOT_EVALUATED(false, false),
  PENDING(false, false),
  PASS(true, false),
  FAIL(false, true),
  OVERRIDE_PASS(true, false),
  OVERRIDE_FAIL(false, true);

  fun passes() = passed
  fun failed() = failed
}
