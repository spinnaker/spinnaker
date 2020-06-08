package com.netflix.spinnaker.keel.validators

import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig

/**
 * A component that can validate or transform a [SubmittedDeliveryConfig] before it is persisted.
 * For example, applying default values, or ensuring semantic correctness.
 */
interface DeliveryConfigProcessor : (SubmittedDeliveryConfig) -> SubmittedDeliveryConfig

/**
 * Applies all [DeliveryConfigProcessor] instances in this list to [deliveryConfig] returning the
 * result.
 */
fun List<DeliveryConfigProcessor>.applyAll(deliveryConfig: SubmittedDeliveryConfig): SubmittedDeliveryConfig =
  fold(deliveryConfig) { conf, processor ->
    processor.invoke(conf)
  }
