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

/**
 * ACTIVE: An Intent is currently being enforced and will be regularly checked for any state to converge on.
 * ISOLATED_ACTIVE: An Intent that is meant to be applied once, but has not been yet.
 * ISOLATED_APPLIED: An Intent that is meant to be applied once, and has been.
 * DELETED: An Intent that has been soft-deleted.
 */
enum class IntentStatus {
  ACTIVE,
  ISOLATED_ACTIVE,
  ISOLATED_APPLIED,
  DELETED
}
