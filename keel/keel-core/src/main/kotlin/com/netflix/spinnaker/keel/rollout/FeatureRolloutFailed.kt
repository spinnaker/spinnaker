package com.netflix.spinnaker.keel.rollout

import com.netflix.spinnaker.keel.api.Resource

/**
 * Published to indicate an attempt to roll out [feature] to [resourceId] seems not to have worked.
 */
data class FeatureRolloutFailed(val feature: String, val resourceId: String) {
  constructor(feature: String, resource: Resource<*>) : this(feature, resource.id)
}
