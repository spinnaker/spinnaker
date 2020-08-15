package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint

/**
 * A [ResourceHandler] is a keel plugin that provides resource state monitoring and actuation capabilities
 * for a specific type of [Resource]. The primary tasks of a [ResourceHandler] are to:
 *
 * 1. Translate the declarative definition of a [Resource], as represented by the matching [ResourceSpec]
 *    (which can be arbitrarily abstract), into a concrete representation of the *desired state* of the resource
 *    that the plugin itself can interpret and act upon. This is implemented in the [desired] method.
 *
 * 2. Provide the *current state* of a [Resource] as it exists in the real world, using the same concrete
 *    representation as that used to represent desired state, so that keel can compare the two and look for
 *    a drift (a.k.a. diff) between desired and current state. This is implemented in the [current] method.
 *
 * 3. Act to resolve the drift when requested by keel. This is done via the [create], [update] and [delete]
 *    methods, which receive a [ResourceDiff] as a parameter.
 *
 * @param S the spec type.
 * @param R the resolved model type.
 */
interface ResourceHandler<S : ResourceSpec, R : Any> : SpinnakerExtensionPoint {
  @JvmDefault
  val name: String
    get() = extensionClass.simpleName

  /**
   * Maps the kind to the implementation type.
   */
  val supportedKind: SupportedKind<S>

  /**
   * Resolve and convert the resource spec into the type that represents the diff-able desired
   * state.
   *
   * The value returned by this method is used as the basis of the diff (with the result of
   * [current] in order to decide whether to call [create]/[update]/[upsert].
   *
   * @param resource the resource as persisted in the Keel database.
   */
  suspend fun desired(resource: Resource<S>): R

  /**
   * Return the current _actual_ representation of what [resource] looks like in the cloud.
   * The entire desired state is passed so that implementations can use whatever identifying
   * information they need to look up the resource.
   *
   * The value returned by this method is used as the basis of the diff (with the result of
   * [desired] in order to decide whether to call [create]/[update]/[upsert].
   *
   * Implementations of this method should not actuate any changes.
   */
  suspend fun current(resource: Resource<S>): R?

  /**
   * Create a resource so that it matches the desired state represented by [resource].
   *
   * By default this just delegates to [upsert].
   *
   * Implement this method and [update] if you need to handle create and update in different ways.
   * Otherwise just implement [upsert].
   *
   * @return a list of tasks launched to actuate the resource.
   */
  @JvmDefault
  suspend fun create(
    resource: Resource<S>,
    resourceDiff: ResourceDiff<R>
  ): List<Task> =
    upsert(resource, resourceDiff)

  /**
   * Update a resource so that it matches the desired state represented by [resource].
   *
   * By default this just delegates to [upsert].
   *
   * Implement this method and [create] if you need to handle create and update in different ways.
   * Otherwise just implement [upsert].
   *
   * @return a list of tasks launched to actuate the resource.
   */
  @JvmDefault
  suspend fun update(
    resource: Resource<S>,
    resourceDiff: ResourceDiff<R>
  ): List<Task> =
    upsert(resource, resourceDiff)

  /**
   * Create or update a resource so that it matches the desired state represented by [resource].
   *
   * You don't need to implement this method if you are implementing [create] and [update]
   * individually.
   *
   * @return a list of tasks launched to actuate the resource.
   */
  @JvmDefault
  suspend fun upsert(
    resource: Resource<S>,
    resourceDiff: ResourceDiff<R>
  ): List<Task> {
    TODO("Not implemented")
  }

  /**
   * Delete a resource as the desired state is that it should no longer exist.
   */
  @JvmDefault
  suspend fun delete(resource: Resource<S>): List<Task> = TODO("Not implemented")

  /**
   * Generate a spec from currently existing resources.
   */
  @JvmDefault
  suspend fun export(exportable: Exportable): S = TODO("Not implemented")

  /**
   * Generates an artifact from a currently existing resource.
   * Note: this only applies to resources that use artifacts, like clusters.
   */
  @JvmDefault
  suspend fun exportArtifact(exportable: Exportable): DeliveryArtifact =
    TODO("Not implemented or not supported with this handler")

  /**
   * @return `true` if this plugin is still busy running a previous actuation for [resource],
   * `false` otherwise.
   */
  @JvmDefault
  suspend fun actuationInProgress(resource: Resource<S>): Boolean = false
}

/**
 * Searches a list of [ResourceHandler]s and returns the first that supports [kind].
 *
 * @throws UnsupportedKind if no appropriate handlers are found in the list.
 */
fun Collection<ResourceHandler<*, *>>.supporting(
  kind: ResourceKind
): ResourceHandler<*, *> =
  find { it.supportedKind.kind == kind } ?: throw UnsupportedKind(kind)

/**
 * Searches a list of [ResourceHandler]s and returns the ones that support the specified [group] and [unqualifiedKind].
 *
 * @throws UnsupportedKind if no appropriate handlers are found in the list.
 */
fun Collection<ResourceHandler<*, *>>.supporting(
  group: String,
  unqualifiedKind: String
): List<ResourceHandler<*, *>> =
  filter { it.supportedKind.kind.group == group && it.supportedKind.kind.kind == unqualifiedKind }
    .also {
      if (it.isEmpty()) {
        throw UnsupportedKind("$group/$unqualifiedKind")
      }
    }

/**
 * Searches a list of [ResourceHandler] and returns the ones that support the specified [specClass].
 */
fun <T : ResourceSpec> Collection<ResourceHandler<*, *>>.supporting(
  specClass: Class<T>
): ResourceHandler<*, *>? =
  find { it.supportedKind.specClass == specClass }

class UnsupportedKind(kind: String) :
  SystemException("No resource handler supporting \"$kind\" is available") {
  constructor(kind: ResourceKind) : this(kind.toString())
}
