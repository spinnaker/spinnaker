/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.api

/**
 * An object which is located in an account and region
 */
interface Locatable<T : RegionSpec> : ResourceSpec {
  val locations: Locations<T>
}

data class Locations<T : RegionSpec>(
  val accountName: String,
  /**
   * If not specified here, this should be derived from [subnet] or use a default VPC name.
   */
  val vpcName: String?,
  /**
   * If not specified here, this should be derived from a default subnet purpose using [vpcName].
   */
  val subnet: String?,
  val regions: Set<T>
)
