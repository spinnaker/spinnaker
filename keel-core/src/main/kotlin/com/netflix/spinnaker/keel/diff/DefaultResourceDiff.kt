package com.netflix.spinnaker.keel.diff

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import de.danielbechler.diff.NodeQueryService
import de.danielbechler.diff.ObjectDiffer
import de.danielbechler.diff.ObjectDifferBuilder
import de.danielbechler.diff.access.Instances
import de.danielbechler.diff.comparison.ComparisonService
import de.danielbechler.diff.differ.BeanDiffer
import de.danielbechler.diff.differ.Differ
import de.danielbechler.diff.differ.DifferDispatcher
import de.danielbechler.diff.differ.DifferFactory
import de.danielbechler.diff.filtering.ReturnableNodeService
import de.danielbechler.diff.introspection.IntrospectionService
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.DiffNode.State.CHANGED

data class DefaultResourceDiff<T : Any>(
  override val desired: T,
  override val current: T?
) : ResourceDiff<T> {
  val diff: DiffNode by lazy {
    differ.compare(
      desired,
      current
    )
  }

  override fun hasChanges(): Boolean = diff.hasChanges()

  override val affectedRootPropertyTypes: List<Class<*>>
    get() = children.map { it.valueType }.toList()

  override val affectedRootPropertyNames: Set<String>
    get() = children.map { it.propertyName }.toSet()

  val children: Set<DiffNode>
    get() = mutableSetOf<DiffNode>()
      .also { nodes ->
        diff.visitChildren { node, visit ->
          visit.dontGoDeeper()
          nodes += node
        }
      }

  override fun toDeltaJson(): Map<String, Any?> =
    JsonVisitor(desired, current, "desired", "current")
      .also { diff.visit(it) }
      .messages

  override fun toUpdateJson(): Map<String, Any?> =
    JsonVisitor(desired, current, "updated", "previous")
      .also { diff.visit(it) }
      .messages

  override fun toDebug(): String =
    DebugVisitor(desired, current)
      .also { diff.visit(it) }
      .toString()

  override fun T?.toMap(): Map<String, Any?>? =
    when (this) {
      null -> null
      else -> mapper.convertValue(this)
    }

  companion object {
    val differ: ObjectDiffer = ObjectDifferBuilder
      .startBuilding()
      .apply {
        differs().register(PolymorphismAwareDifferFactory(this))
      }
      .build()
    val mapper: ObjectMapper = configuredObjectMapper()
  }
}

fun <T : Any> ResourceDiff<Map<String, T>>.toIndividualDiffs(): List<ResourceDiff<T>> =
  desired
    .map { (key, desired) ->
      DefaultResourceDiff(desired, current?.get(key))
    }

private class PolymorphismAwareDifferFactory(
  private val objectDifferBuilder: ObjectDifferBuilder
) : DifferFactory {
  override fun createDiffer(
    differDispatcher: DifferDispatcher,
    nodeQueryService: NodeQueryService
  ): Differ = object : Differ {
    // ugh, wish I could just get the already built one, but there's no hook to access it
    private val delegate = BeanDiffer(
      differDispatcher,
      IntrospectionService(objectDifferBuilder),
      ReturnableNodeService(objectDifferBuilder),
      ComparisonService(objectDifferBuilder),
      IntrospectionService(objectDifferBuilder)
    )

    override fun accepts(type: Class<*>): Boolean =
      // we don't want to handle collections as the existing differ works
      delegate.accepts(type) && !Collection::class.java.isAssignableFrom(type) && !Map::class.java.isAssignableFrom(type)

    override fun compare(parentNode: DiffNode?, instances: Instances): DiffNode =
      if (instances.areDifferentSubTypes()) {
        DiffNode(parentNode, instances.sourceAccessor, instances.type)
          .apply { state = CHANGED }
      } else {
        delegate.compare(parentNode, instances)
      }

    private fun Instances.areDifferentSubTypes(): Boolean =
      if (working == null || base == null) {
        // will get resolved as ADDED or REMOVED by regular differ
        false
      } else {
        // this is the case we want to handle specially
        working.javaClass != base.javaClass
      }
  }
}
