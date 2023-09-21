package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.rollout.RolloutStatus

interface FeatureRolloutRepository {
  fun markRolloutStarted(feature: String, resourceId: String)
  fun rolloutStatus(feature: String, resourceId: String): Pair<RolloutStatus, Int>
  fun updateStatus(feature: String, resourceId: String, status: RolloutStatus)
}

fun FeatureRolloutRepository.markRolloutStarted(feature: String, resource: Resource<*>) =
  markRolloutStarted(feature, resource.id)

fun FeatureRolloutRepository.rolloutStatus(feature: String, resource: Resource<*>) =
  rolloutStatus(feature, resource.id)
