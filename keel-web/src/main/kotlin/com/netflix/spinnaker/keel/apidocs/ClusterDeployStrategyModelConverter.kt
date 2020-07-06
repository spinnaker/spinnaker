package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.Highlander
import com.netflix.spinnaker.keel.api.RedBlack
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

@Component
class ClusterDeployStrategyModelConverter : SubtypesModelConverter<ClusterDeployStrategy>(ClusterDeployStrategy::class.java) {
  override val subTypes = listOf(RedBlack::class.java, Highlander::class.java)
}

@Component
class ClusterDeployStrategySubtypesSchemaCustomizer : AbstractSchemaCustomizer() {
  override fun supports(type: Class<*>) = type in listOf(RedBlack::class.java, Highlander::class.java)

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    eachSchemaProperty(ClusterDeployStrategy::isStaggered) {
      schema.markOptional(it)
    }
  }
}
