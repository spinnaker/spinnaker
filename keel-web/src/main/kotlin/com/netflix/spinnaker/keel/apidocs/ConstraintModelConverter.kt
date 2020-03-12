package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

/**
 * Adds [Schema]s for the available sub-types of [Constraint]. This cannot be done with
 * annotations on [Constraint] as the sub-types are not known at compile time.
 */
@Component
class ConstraintModelConverter(
  evaluators: List<ConstraintEvaluator<*>>
) : SubtypesModelConverter<Constraint>(Constraint::class.java) {

  override val subTypes = evaluators.map { it.supportedType.type }

  override val discriminator: String? = Constraint::type.name

  override val mapping: Map<String, Class<out Constraint>> = evaluators.associate {
    it.supportedType.name to it.supportedType.type
  }
}
