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

import com.netflix.spinnaker.keel.api.UID

/**
 * TODO: Docs.
 */
interface ConstraintRepository {

  fun storeConstraintState(state: ConstraintState)

  fun getConstraintState(deliveryConfigName: String, environmentName: String, artifactVersion: String, type: String, artifactReference: String?): ConstraintState?

  fun getConstraintStateById(uid: UID): ConstraintState?

  fun deleteConstraintState(deliveryConfigName: String, environmentName: String, type: String)

  fun constraintStateFor(application: String): List<ConstraintState>

  fun constraintStateFor(deliveryConfigName: String, environmentName: String, limit: Int): List<ConstraintState>

  fun constraintStateFor(deliveryConfigName: String, environmentName: String, artifactVersion: String): List<ConstraintState>
}
