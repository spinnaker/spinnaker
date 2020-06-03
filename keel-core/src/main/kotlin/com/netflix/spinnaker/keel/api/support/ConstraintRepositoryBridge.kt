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
package com.netflix.spinnaker.keel.api.support

import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.api.constraints.ConstraintRepository
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.persistence.KeelRepository
import org.springframework.stereotype.Component

/**
 * Bridges [ConstraintRepository] to the underlying [KeelRepository] implementation.
 *
 * [ConstraintRepository] and this bridge are intended just to reduce API exposure and provide a stable API for
 * plugin developers while the underlying repository can continue to evolve separately.
 */
@Component
class ConstraintRepositoryBridge(
  private val keelRepository: KeelRepository
) : ConstraintRepository {
  override fun storeConstraintState(state: ConstraintState) {
    keelRepository.storeConstraintState(state)
  }

  override fun getConstraintState(deliveryConfigName: String, environmentName: String, artifactVersion: String, type: String): ConstraintState? {
    return keelRepository.getConstraintState(deliveryConfigName, environmentName, artifactVersion, type)
  }

  override fun getConstraintStateById(uid: UID): ConstraintState? {
    return keelRepository.getConstraintStateById(uid)
  }

  override fun deleteConstraintState(deliveryConfigName: String, environmentName: String, type: String) {
    return keelRepository.deleteConstraintState(deliveryConfigName, environmentName, type)
  }

  override fun constraintStateFor(application: String): List<ConstraintState> {
    return keelRepository.constraintStateFor(application)
  }

  override fun constraintStateFor(deliveryConfigName: String, environmentName: String, limit: Int): List<ConstraintState> {
    return keelRepository.constraintStateFor(deliveryConfigName, environmentName, limit)
  }

  override fun constraintStateFor(deliveryConfigName: String, environmentName: String, artifactVersion: String): List<ConstraintState> {
    return keelRepository.constraintStateFor(deliveryConfigName, environmentName, artifactVersion)
  }
}
