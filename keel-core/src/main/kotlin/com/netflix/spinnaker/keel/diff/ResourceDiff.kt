package com.netflix.spinnaker.keel.diff

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import de.danielbechler.diff.NodeQueryService
import de.danielbechler.diff.ObjectDiffer
import de.danielbechler.diff.ObjectDifferBuilder
import de.danielbechler.diff.differ.Differ
import de.danielbechler.diff.differ.DifferDispatcher
import de.danielbechler.diff.differ.DifferFactory
import de.danielbechler.diff.node.DiffNode

data class ResourceDiff<T : Any>(
  val desired: T,
  val current: T?
) {
  private val desiredAsMap: Map<String, Any?> by lazy { desired.toMap()!! }
  private val currentAsMap: Map<String, Any?>? by lazy { current.toMap() }

  val diff: DiffNode by lazy {
    differ.compare(
      desired,
      current
    )
  }

  fun hasChanges(): Boolean = diff.hasChanges()

  val affectedRootPropertyTypes: List<Class<*>>
    get() = mutableListOf<Class<*>>()
      .also { types ->
        diff.visitChildren { node, visit ->
          visit.dontGoDeeper()
          types += node.valueType
        }
      }

  fun toDeltaJson(): Map<String, Any?> =
    JsonVisitor(desired, current, "desired", "current")
      .also { diff.visit(it) }
      .messages

  fun toUpdateJson(): Map<String, Any?> =
    JsonVisitor(desired, current, "updated", "previous")
      .also { diff.visit(it) }
      .messages

  fun toDebug(): String =
    DebugVisitor(desired, current)
      .also { diff.visit(it) }
      .toString()

  private fun T?.toMap(): Map<String, Any?>? =
    when (this) {
      null -> null
      else -> mapper.convertValue(this)
    }

  companion object {
    val differ: ObjectDiffer = ObjectDifferBuilder
      .startBuilding()
      .apply {
        //        differs().register(PolymorphismAwareDifferFactory)
      }
      .build()
    val mapper: ObjectMapper = configuredObjectMapper()
  }
}

private object PolymorphismAwareDifferFactory : DifferFactory {
  override fun createDiffer(differDispatcher: DifferDispatcher, nodeQueryService: NodeQueryService): Differ {
    TODO("not implemented")
  }
}
