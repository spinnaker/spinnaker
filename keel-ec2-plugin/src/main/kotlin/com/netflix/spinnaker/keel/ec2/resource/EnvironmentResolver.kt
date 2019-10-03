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
package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.model.EchoNotification
import com.netflix.spinnaker.keel.model.toEchoNotification
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository

/**
 * Helper class for resource handlers to get specific information out of the environment for a resource.
 */
class EnvironmentResolver(
  private val deliveryConfigRepository: DeliveryConfigRepository
) {
  fun getNotificationsFor(resourceId: ResourceId): List<EchoNotification> =
    deliveryConfigRepository.environmentFor(resourceId)?.notifications?.map { it.toEchoNotification() } ?: emptyList()
}
