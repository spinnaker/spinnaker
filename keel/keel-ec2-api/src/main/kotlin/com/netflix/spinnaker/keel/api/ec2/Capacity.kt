package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.CapacitySpec

sealed class Capacity {
  abstract val min: Int
  abstract val max: Int
  abstract val desired: Int

  data class DefaultCapacity(
    override val min: Int,
    override val max: Int,
    override val desired: Int
  ) : Capacity() {
    constructor(spec: CapacitySpec) : this(spec.min, spec.max, requireNotNull(spec.desired) {
      "desired capacity must be specified if a cluster does not use scaling policies"
    })
  }

  /**
   * The only difference with an auto-scaling capacity is that [desired] is ignored when diffing. Because it must still
   * be supplied when cloning a server group we need to know what it is.
   */
  data class AutoScalingCapacity(
    override val min: Int,
    override val max: Int,
    @get:ExcludedFromDiff
    override val desired: Int
  ) : Capacity() {
    constructor(spec: CapacitySpec) : this(spec.min, spec.max, spec.desired ?: spec.min)
  }
}
