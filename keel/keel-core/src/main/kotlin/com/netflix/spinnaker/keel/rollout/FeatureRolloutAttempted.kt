package com.netflix.spinnaker.keel.rollout

import com.netflix.spinnaker.keel.api.Resource

/**
 * Event triggered to indicate an attempt to roll out [feature] to [respourceId] was started.
 */
class FeatureRolloutAttempted(val feature: String, val resourceId: String) {
  constructor(feature: String, resource: Resource<*>) : this(feature, resource.id)
}
