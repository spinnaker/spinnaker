package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.events.ConstraintStateChanged
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * An event listener that listens to [ConstraintStateChanged] events and relays them to the
 * corresponding [StatefulConstraintEvaluator] for processing.
 *
 * Note: in theory, constraint evaluators should be able to subscribe to these events directly,
 * but we've found that Spring auto-wiring does not seem to work for external plugins, so this
 * provides an alternative.
 */
@Component
class ConstraintStateChangedRelay(
  private val statefulEvaluators: List<StatefulConstraintEvaluator<*, *>>
) {
  @EventListener(ConstraintStateChanged::class)
  fun onConstraintStateChanged(event: ConstraintStateChanged) {
    statefulEvaluators.supporting(event.constraint)?.onConstraintStateChanged(event)
  }

  private fun List<StatefulConstraintEvaluator<*, *>>.supporting(constraint: Constraint) =
    this.firstOrNull { it.supportedType.name == constraint.type }
}
