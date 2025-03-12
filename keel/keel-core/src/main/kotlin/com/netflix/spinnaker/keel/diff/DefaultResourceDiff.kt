package com.netflix.spinnaker.keel.diff

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
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
import de.danielbechler.diff.identity.IdentityStrategy
import de.danielbechler.diff.inclusion.Inclusion
import de.danielbechler.diff.inclusion.Inclusion.EXCLUDED
import de.danielbechler.diff.inclusion.Inclusion.INCLUDED
import de.danielbechler.diff.inclusion.InclusionResolver
import de.danielbechler.diff.introspection.IntrospectionService
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import java.lang.reflect.Modifier
import java.time.Duration
import java.time.Instant

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
        comparison().apply {
          ofType(Instant::class.java).toUseEqualsMethod()
          ofType(Duration::class.java).toUseEqualsMethod()
        }
        inclusion().resolveUsing(object : InclusionResolver {
          override fun getInclusion(node: DiffNode): Inclusion =
            if (node.getPropertyAnnotation<ExcludedFromDiff>() != null) EXCLUDED else INCLUDED

          override fun enablesStrictIncludeMode() = false
        })
        identity().apply {
          // These tell the differ how to match items within collections before comparing, so that it
          // doesn't interpret changes in collections as if the entire parent object is different.
          ofCollectionItems(DeliveryConfig::class.java, "artifacts").via { working, base ->
            (working as? DeliveryArtifact)?.reference == (base as? DeliveryArtifact)?.reference
          }
          ofCollectionItems(DeliveryConfig::class.java, "environments").via { working, base ->
            (working as? Environment)?.name == (base as? Environment)?.name
          }
          ofCollectionItems(Environment::class.java, "resources").via { working, base ->
            (working as? Resource<*>)?.id == (base as? Resource<*>)?.id
          }
        }
        differs().register(PolymorphismAwareDifferFactory(this))
      }
      .build()
    val mapper: ObjectMapper = configuredObjectMapper()
  }
}

private inline fun <reified T : Annotation> DiffNode.getPropertyAnnotation() =
  getPropertyAnnotation(T::class.java)

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
      !type.isFinal && !type.isCollection && !type.isMap

    override fun compare(parentNode: DiffNode?, instances: Instances): DiffNode =
      if (instances.areDifferentSubTypes()) {
        DiffNode(parentNode, instances.sourceAccessor, instances.type).apply { state = CHANGED }
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

    private val Class<*>.isFinal: Boolean
      get() = Modifier.isFinal(modifiers)

    private val Class<*>.isCollection: Boolean
      get() = Collection::class.java.isAssignableFrom(this)

    private val Class<*>.isMap: Boolean
      get() = Map::class.java.isAssignableFrom(this)
  }
}
